package net.mccg.web;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import java.io.IOException;

import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.FormService;
import org.activiti.engine.HistoryService;
import org.activiti.engine.ProcessEngine;
import org.activiti.engine.ProcessEngineConfiguration;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.form.FormData;
import org.activiti.engine.form.FormProperty;
import org.activiti.engine.history.HistoricActivityInstance;
import org.activiti.engine.impl.cfg.StandaloneProcessEngineConfiguration;
import org.activiti.engine.impl.form.DateFormType;
import org.activiti.engine.impl.form.LongFormType;
import org.activiti.engine.impl.form.StringFormType;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.ProcessDefinition;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;

@WebServlet(value = "/login/*")
public class LoginServlet extends HttpServlet {

    private ProcessInstance processInstance = null;
    private TaskService taskService = null;
    private FormService formService = null;
    private HistoryService historyService = null;
    private ProcessDefinition processDefinition = null;
    private RuntimeService runtimeService = null;

    @Override
    public void init() throws ServletException {

        ProcessEngineConfiguration cfg = new StandaloneProcessEngineConfiguration()
                .setJdbcUrl("jdbc:h2:mem:activiti;DB_CLOSE_DELAY=1000")
                .setJdbcUsername("sa")
                .setJdbcPassword("")
                .setJdbcDriver("org.h2.Driver")
                .setDatabaseSchemaUpdate(ProcessEngineConfiguration.DB_SCHEMA_UPDATE_TRUE);

        ProcessEngine processEngine = cfg.buildProcessEngine();
        String pName = processEngine.getName();
        String ver = ProcessEngine.VERSION;
        System.out.println("ProcessEngine [" + pName + "] Version: [" + ver + "]");

        RepositoryService repositoryService = processEngine.getRepositoryService();
        Deployment deployment = repositoryService.createDeployment()
                .addClasspathResource("onboarding.bpmn20.xml").deploy();
        processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deployment.getId()).singleResult();
        System.out.println(
                "Found process definition ["
                        + processDefinition.getName() + "] with id ["
                        + processDefinition.getId() + "]");

        runtimeService = processEngine.getRuntimeService();
        processInstance = runtimeService
                .startProcessInstanceByKey("onboarding");
        System.out.println("Onboarding process started with process instance id ["
                + processInstance.getProcessInstanceId()
                + "] key [" + processInstance.getProcessDefinitionKey() + "]");

        taskService = processEngine.getTaskService();
        formService = processEngine.getFormService();
        historyService = processEngine.getHistoryService();


    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String response = "something";

        processInstance = runtimeService
                .startProcessInstanceByKey("onboarding");


        while (processInstance != null && !processInstance.isEnded()) {
            List<Task> tasks = taskService.createTaskQuery()
                    .taskCandidateGroup("managers").list();
            System.out.println("Active outstanding tasks: [" + tasks.size() + "]");
            for (int i = 0; i < tasks.size(); i++) {
                Task task = tasks.get(i);
                System.out.println("Processing Task [" + task.getName() + "]");
                Map<String, Object> variables = new HashMap<String, Object>();
                FormData formData = formService.getTaskFormData(task.getId());
                for (FormProperty formProperty : formData.getFormProperties()) {
                    if (StringFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(formProperty.getName() + "?");
                        variables.put(formProperty.getId(), "greg");
                    } else if (LongFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(formProperty.getName() + "? (Must be a whole number)");
                        variables.put(formProperty.getId(), 2l);

                    } else if (DateFormType.class.isInstance(formProperty.getType())) {
                        System.out.println(formProperty.getName() + "? (Must be a date m/d/yy)");
                        DateFormat dateFormat = new SimpleDateFormat("m/d/yy");
                        Date value = null;
                        try {
                            value = dateFormat.parse("01/01/72");
                        } catch (ParseException e) {
                        }
                        variables.put(formProperty.getId(), value);
                    } else {
                        System.out.println("<form type not supported>");
                    }
                }
                taskService.complete(task.getId(), variables);

                HistoricActivityInstance endActivity = null;
                List<HistoricActivityInstance> activities =
                        historyService.createHistoricActivityInstanceQuery()
                                .processInstanceId(processInstance.getId()).finished()
                                .orderByHistoricActivityInstanceEndTime().asc()
                                .list();
                for (HistoricActivityInstance activity : activities) {
                    if (activity.getActivityType() == "startEvent") {
                        System.out.println("BEGIN " + processDefinition.getName()
                                + " [" + processInstance.getProcessDefinitionKey()
                                + "] " + activity.getStartTime());
                    }
                    if (activity.getActivityType() == "endEvent") {
                        endActivity = activity;
                    } else {
                        System.out.println("-- " + activity.getActivityName()
                                + " [" + activity.getActivityId() + "] "
                                + activity.getDurationInMillis() + " ms");
                    }
                }
                if (endActivity != null) {
                    System.out.println("-- " + endActivity.getActivityName()
                            + " [" + endActivity.getActivityId() + "] "
                            + endActivity.getDurationInMillis() + " ms");
                    System.out.println("COMPLETE " + processDefinition.getName() + " ["
                            + processInstance.getProcessDefinitionKey() + "] "
                            + endActivity.getEndTime());
                }
            }
            processInstance = runtimeService.createProcessInstanceQuery()
                    .processInstanceId(processInstance.getId()).singleResult();
        }

        resp.getWriter().write(response);
    }

}
