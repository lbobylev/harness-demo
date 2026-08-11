package dev.harness.agent.plan;

import com.fasterxml.jackson.annotation.JsonIgnore;
import dev.harness.agent.ai.AiUsage;
import dev.harness.agent.run.HarnessErrorCode;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlanNode implements Serializable {

    private String id;

    private String agent;

    private List<ArgumentBinding> arguments = List.of();

    private List<String> deps = List.of();

    private NodeStatus status = NodeStatus.PENDING;

    private Object result;

    private AiUsage usage;

    private String error;

    private HarnessErrorCode errorCode;

    public PlanNode() {
    }

    public PlanNode(String id, String agent, List<String> deps) {
        this(id, agent, List.of(), deps);
    }

    public PlanNode(String id, String agent, List<ArgumentBinding> arguments, List<String> deps) {
        this.id = id;
        this.agent = agent;
        setArguments(arguments);
        setDeps(deps);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getAgent() {
        return agent;
    }

    public void setAgent(String agent) {
        this.agent = agent;
    }

    public List<ArgumentBinding> getArguments() {
        return arguments;
    }

    public void setArguments(List<ArgumentBinding> arguments) {
        this.arguments = arguments == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(arguments));
    }

    public List<String> getDeps() {
        return deps;
    }

    public void setDeps(List<String> deps) {
        this.deps = deps == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(deps));
    }

    @JsonIgnore
    public NodeStatus getStatus() {
        return status;
    }

    public void setStatus(NodeStatus status) {
        this.status = status == null ? NodeStatus.PENDING : status;
    }

    @JsonIgnore
    public Object getResult() {
        return result;
    }

    @JsonIgnore
    public void setResult(Object result) {
        this.result = result;
    }

    @JsonIgnore
    public AiUsage getUsage() {
        return usage;
    }

    public void setUsage(AiUsage usage) {
        this.usage = usage;
    }

    @JsonIgnore
    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    @JsonIgnore
    public HarnessErrorCode getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(HarnessErrorCode errorCode) {
        this.errorCode = errorCode;
    }

    @JsonIgnore
    public boolean isPending() {
        return status == NodeStatus.PENDING;
    }

    @JsonIgnore
    public boolean isDone() {
        return status == NodeStatus.DONE;
    }

    @JsonIgnore
    public boolean isFailed() {
        return status == NodeStatus.FAILED;
    }

    @JsonIgnore
    public boolean isSkipped() {
        return status == NodeStatus.SKIPPED;
    }
}
