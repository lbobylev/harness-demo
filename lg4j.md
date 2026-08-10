# Выполнение DAG через LangGraph4j

Собственный `DagExecutor` хорошо показывает механику: найти готовые узлы, запустить их параллельно, дождаться завершения уровня и перейти дальше. Tу же идею можно переложить на LangGraph4j. В этом случае harness по-прежнему владеет планированием, валидацией, бюджетом и обработкой ошибок, а LangGraph4j используется как runtime для исполнения уже проверенной topology.

Planner по-прежнему возвращает обычный доменный `Plan`. Это внешний контракт harness: его можно получить через structured output, сохранить, проверить, показать в trace и использовать в тестах. `StateGraph` - это уже runtime-объект, удобный для исполнения, но плохой формат обмена между LLM и orchestration-слоем. Узел валидации плана проверяет ссылки на зависимости, отсутствие циклов, существование инструментов, обязательные аргументы, корректность `NODE_RESULT` bindings и наличие единственного финального synthesis-узла. После этого появляется отдельный адаптерный слой - `Lg4jPlanShape`. Его задача понять, можно ли валидный план выразить через поддерживаемую форму LangGraph4j-графа.

```java
record Lg4jPlanShape(List<List<String>> branches, List<String> tail) {
}
```

В текущей реализации shape описывает несколько независимых линейных веток и общий хвост. Каждая ветка может содержать сколько угодно звеньев. Узлы внутри одной ветки выполняются последовательно, потому что соединены edge-ами друг с другом. Сами ветки независимы и запускаются параллельно. После завершения веток выполнение сходится в общий `tail`.

```text
START
  |
fork
  |---- metrics -> compare --------|
  |---- logs -> signature ---------|--> assemble -> correlate -> test -> report -> END
  |---- traces --------------------|
```

`Lg4jPlanShapeAnalyzer` извлекает такую форму из уже проверенного `Plan`. Например, он находит общий хвост по смысловым tool names: `assemble_evidence`, `correlate`, `test_hypothesis`, `build_incident_report`. Затем для каждой зависимости `assemble_evidence` восстанавливает линейную evidence-ветку до ее корня. Если план валиден как DAG, но не попадает в поддерживаемую форму, analyzer явно отказывает с `unsupported lg4j plan shape`. Это осознанное ограничение: builder остается простым, а validation не размазывается по двум местам.

Дальше `Lg4jPlanGraphBuilder` механически превращает shape в `StateGraph`. Он получает сам `Plan`, извлеченный `Lg4jPlanShape` и функцию, которая превращает каждый `PlanNode` в LangGraph4j node action.

Для каждой ветки builder создает отдельный subgraph:

```java
private StateGraph<Lg4jPlanExecutionState> branchGraph(
        List<String> branch,
        Map<String, PlanNode> nodesById,
        Function<PlanNode, AsyncNodeAction<Lg4jPlanExecutionState>> nodeAction) throws GraphStateException {
    var graph = new StateGraph<>(Lg4jPlanExecutionState.SCHEMA, Lg4jPlanExecutionState::new);

    for (var nodeId : branch) {
        graph.addNode(nodeId, nodeAction.apply(requireNode(nodesById, nodeId)));
    }

    graph.addEdge(START, branch.getFirst());
    for (int i = 1; i < branch.size(); i++) {
        graph.addEdge(branch.get(i - 1), branch.get(i));
    }
    graph.addEdge(branch.getLast(), END);

    return graph;
}
```

Так ветка любой длины становится compiled subgraph. Для parent graph она выглядит как один узел, но внутри сохраняет свой последовательный порядок выполнения.

Parent graph добавляет общий fork, подключает к нему все branch subgraphs и затем сводит их в первый узел общего хвоста:

```java
graph.addNode(FORK, node_async(state -> Map.of()));
graph.addEdge(START, FORK);

for (int i = 0; i < shape.branches().size(); i++) {
    var branchId = BRANCH_PREFIX + i;
    graph.addNode(branchId, branchGraph(shape.branches().get(i), nodesById, nodeAction).compile());
    graph.addEdge(FORK, branchId);
    graph.addEdge(branchId, shape.tail().getFirst());
}
```

После fan-in builder добавляет tail как обычную линейную цепочку:

```java
for (var nodeId : shape.tail()) {
    graph.addNode(nodeId, nodeAction.apply(requireNode(nodesById, nodeId)));
}

for (int i = 1; i < shape.tail().size(); i++) {
    graph.addEdge(shape.tail().get(i - 1), shape.tail().get(i));
}

graph.addEdge(shape.tail().getLast(), END);
```

На этом этапе LangGraph4j отвечает только за порядок и параллельность исполнения. Семантика harness остается внутри node action. `Lg4jPlanExecutor` строит graph, компилирует его и запускает:

```java
return graphBuilder
        .build(plan, shape, node -> node_async(state -> executeNode(budget, semaphore, node, state)))
        .compile()
        .invoke(initialState(budget))
        .orElseThrow(() -> new IllegalStateException("LangGraph4j plan graph returned no final state"));
```

`executeNode()` делает ту же работу, которую должен делать любой production executor: проверяет failed или skipped dependencies, списывает tool call из бюджета, материализует аргументы, вызывает инструмент и возвращает обновление состояния. `LITERAL` arguments попадают в tool call напрямую, `NODE_RESULT` arguments берутся из результатов предыдущих узлов.

Результаты выполнения хранятся в `Lg4jPlanExecutionState`: отдельно `results`, `statuses`, `errors` и snapshot бюджета. Для этих map используются channels с merge-логикой, чтобы параллельные ветки могли независимо записывать свои результаты в общее состояние.

```java
static final Map<String, Channel<?>> SCHEMA = Map.of(
        RESULTS, Channels.<Map<String, Object>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new),
        STATUSES, Channels.<Map<String, NodeStatus>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new),
        ERRORS, Channels.<Map<String, String>>base(Lg4jPlanExecutionState::merge, LinkedHashMap::new));
```

После завершения compiled graph execution state накладывается обратно на доменный `Plan`:

```java
for (var node : plan.nodes()) {
    var nodeId = node.getId();
    node.setStatus(executionState.statuses().getOrDefault(nodeId, node.getStatus()));
    node.setResult(executionState.result(nodeId));
    node.setError(executionState.errors().get(nodeId));
}
```

Это важная граница: LangGraph4j не протекает наружу. Внешние слои harness продолжают работать с тем же `Plan`, только теперь у его узлов заполнены `status`, `result` и `error`. Verifier не знает, выполнялся план собственным `DagExecutor` или через LangGraph4j. Он видит обычную доменную модель и проверяет финальный synthesis-узел, его зависимости и итоговый отчет.

В итоге LangGraph4j здесь используется не как готовый agent, которому дали tools и позволили вести loop. Его роль уже и уже полезнее: это execution backend для валидированного плана. LLM строит `Plan`, harness проверяет структуру, shape analyzer извлекает поддерживаемую форму, graph builder строит runtime-граф, а LangGraph4j исполняет параллельные ветки.

Такое разделение покупает три вещи. Во-первых, planner не привязан к internals конкретного framework-а и возвращает простой сериализуемый контракт. Во-вторых, validation остается в одном месте и не дублируется в graph builder-е. В-третьих, параллельность становится свойством runtime topology: независимые ветки идут одновременно, а последовательность внутри каждой ветки и общего tail остается явной.
