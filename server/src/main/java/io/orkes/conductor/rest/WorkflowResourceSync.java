package io.orkes.conductor.rest;

import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.conductor.common.metadata.workflow.StartWorkflowRequest;
import com.netflix.conductor.common.run.Workflow;
import com.netflix.conductor.service.WorkflowService;
import io.orkes.conductor.common.model.WorkflowRun;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.concurrent.*;

import static com.netflix.conductor.rest.config.RequestMappingConstants.WORKFLOW;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping(WORKFLOW)
@Slf4j
@RequiredArgsConstructor
public class WorkflowResourceSync {

    public static final String REQUEST_ID_KEY = "_X-Request-Id";

    private final WorkflowService workflowService;

    private final ScheduledExecutorService executionMonitor = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    @PostConstruct
    public void startMonitor() {
        log.info("Starting execution monitors");
    }

    @PostMapping(value = "execute/{name}/{version}", produces = APPLICATION_JSON_VALUE)
    @Operation(summary = "Execute a workflow synchronously", tags = "workflow-resource")
    @SneakyThrows
    public WorkflowRun executeWorkflow(
            @PathVariable("name") String name,
            @PathVariable(value = "version", required = false) Integer version,
            @RequestParam(value = "requestId", required = true) String requestId,
            @RequestParam(value = "waitUntilTaskRef", required = false) String waitUntilTaskRef,
            @RequestBody StartWorkflowRequest request) {

        request.setName(name);
        request.setVersion(version);
        String workflowId = workflowService.startWorkflow(request);
        request.getInput().put(REQUEST_ID_KEY, requestId);
        Workflow workflow = workflowService.getExecutionStatus(workflowId, true);
        if(workflow.getStatus().isTerminal() || workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equalsIgnoreCase(waitUntilTaskRef))) {
            return toWorkflowRun(workflow);
        }
        int maxTimeInMilis = 5_000;        //5 sec
        int sleepTime = 100;                //millis
        int loopCount = maxTimeInMilis / sleepTime;
        for (int i = 0; i < loopCount; i++) {
            Uninterruptibles.sleepUninterruptibly(100, TimeUnit.MILLISECONDS);
            workflow = workflowService.getExecutionStatus(workflowId, true);
            if(workflow.getStatus().isTerminal() || workflow.getTasks().stream().anyMatch(t -> t.getReferenceTaskName().equalsIgnoreCase(waitUntilTaskRef))) {
                return toWorkflowRun(workflow);
            }
        }
        workflow = workflowService.getExecutionStatus(workflowId, true);
        return toWorkflowRun(workflow);
    }

    public static WorkflowRun toWorkflowRun(Workflow workflow) {
        WorkflowRun run = new WorkflowRun();

        run.setWorkflowId(workflow.getWorkflowId());
        run.setRequestId((String) workflow.getInput().get(REQUEST_ID_KEY));
        run.setCorrelationId(workflow.getCorrelationId());
        run.setInput(workflow.getInput());
        run.setCreatedBy(workflow.getCreatedBy());
        run.setCreateTime(workflow.getCreateTime());
        run.setOutput(workflow.getOutput());
        run.setTasks(new ArrayList<>());
        workflow.getTasks().forEach(task -> run.getTasks().add(task));
        run.setPriority(workflow.getPriority());
        if(workflow.getUpdateTime() != null) {
            run.setUpdateTime(workflow.getUpdateTime());
        }
        run.setStatus(Workflow.WorkflowStatus.valueOf(workflow.getStatus().name()));
        run.setVariables(workflow.getVariables());

        return run;
    }
}
