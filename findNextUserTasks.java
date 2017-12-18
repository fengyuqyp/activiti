package com.kf.oa.workflow.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Resource;

import org.activiti.engine.ProcessEngines;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.impl.RepositoryServiceImpl;
import org.activiti.engine.impl.bpmn.behavior.UserTaskActivityBehavior;
import org.activiti.engine.impl.javax.el.ExpressionFactory;
import org.activiti.engine.impl.javax.el.ValueExpression;
import org.activiti.engine.impl.juel.ExpressionFactoryImpl;
import org.activiti.engine.impl.juel.SimpleContext;
import org.activiti.engine.impl.persistence.entity.ExecutionEntity;
import org.activiti.engine.impl.persistence.entity.ProcessDefinitionEntity;
import org.activiti.engine.impl.pvm.PvmActivity;
import org.activiti.engine.impl.pvm.PvmTransition;
import org.activiti.engine.impl.pvm.process.ActivityImpl;
import org.activiti.engine.impl.task.TaskDefinition;
import org.activiti.engine.runtime.Execution;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import com.kf.oa.workflow.entity.FlowInfo;
import com.kf.oa.workflow.service.ICreateFlow;

/**
 * ��ȡ�������̽ڵ����
 * @author qyp
 */
@Service("createFlow")
public class CreateFlow implements ICreateFlow {

	private TaskService taskService = ProcessEngines.getDefaultProcessEngine().getTaskService() ;
	private RuntimeService runtimeService = ProcessEngines.getDefaultProcessEngine().getRuntimeService();
//	private RepositoryServiceImpl repositoryService = ProcessEngines.getDefaultProcessEngine().getRepositoryService();
	@Resource
	private RepositoryServiceImpl repositoryService;
	
	//��������ʱ���õĲ���
	Map<String, Object> runTimeVariables = new HashMap<>();

	/**
	 * ȡ����ȡ���Ľڵ�����ò�����group��taskName
	 */
	@Override
	public List<FlowInfo> getFlowList(String taskId) throws Exception {
		String processInstanceId = taskService.createTaskQuery().taskId(taskId).singleResult().getProcessInstanceId();
		List<TaskDefinition> taskList = getNextTaskInfo(processInstanceId);
		List<FlowInfo> flowInfoList = new LinkedList<>();
		for (TaskDefinition taskDefinition : taskList) {
			FlowInfo flowInfo = new FlowInfo(taskDefinition.getNameExpression().toString(), taskDefinition.getCandidateGroupIdExpressions().toString(), taskDefinition.getKey());
			flowInfoList.add(flowInfo);
		}
		return flowInfoList;
	}
	
    /** 
     * ��������������ȡ��ǰ�ڵ������нڵ�
     * @param String taskId     ����Id��Ϣ  
     * @return  ��һ���û������û�����Ϣ  
     * @throws Exception 
     */ 
    private List<TaskDefinition> getNextTaskInfo(String processInstanceId) throws Exception {  
		
		runTimeVariables = getAllRunTimeVariables(processInstanceId);
		
        ProcessDefinitionEntity processDefinitionEntity = null;  

        String id = null;  

        TaskDefinition task = null;  
        List<TaskDefinition> taskList = new ArrayList<>();

        //��ȡ���̷���Id��Ϣ   
        String definitionId = runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult().getProcessDefinitionId();  
        
        processDefinitionEntity = (ProcessDefinitionEntity) ((RepositoryServiceImpl) repositoryService).getDeployedProcessDefinition(definitionId);
        ExecutionEntity execution = (ExecutionEntity) runtimeService.createProcessInstanceQuery().processInstanceId(processInstanceId).singleResult();  

        //��ǰ���̽ڵ�Id��Ϣ   
        String activitiId = execution.getActivityId();

        //��ȡ�������нڵ���Ϣ
        List<ActivityImpl> activitiList = processDefinitionEntity.getActivities();

        //�������нڵ���Ϣ
        for(ActivityImpl activityImpl : activitiList){
            id = activityImpl.getId();
            if (activitiId.equals(id)) {
                //��ȡ��һ���ڵ���Ϣ
            	TaskDefinition nowTask = ((UserTaskActivityBehavior) activityImpl.getActivityBehavior()).getTaskDefinition();
            	if(nowTask != null)
                	taskList.add(nowTask);
                task = nextTaskDefinition(activityImpl, activityImpl.getId(), null, processInstanceId);
                if(task != null)
                	taskList.add(task);
                break;
            }
        }
        
        while(task != null && !(task.getKey().contains("end"))){
        	for(ActivityImpl activityImpl : activitiList){      
	            id = activityImpl.getId();     
	            if ((task.getKey()).equals(id)) {
	                //��ȡ��һ���ڵ���Ϣ   
	                task = nextTaskDefinition(activityImpl, activityImpl.getId(), null, processInstanceId); 
	                if(task != null)
	                	taskList.add(task);
	                break;
	            }
	        }
        	if(task==null) break;
        }
        return taskList;  
    }  

    /**  
     * ��һ������ڵ���Ϣ,  
     *  
     * �����һ���ڵ�Ϊ�û�������ֱ�ӷ���,  
     *  
     * �����һ���ڵ�Ϊ��������, ��ȡ��������Id��Ϣ, ������������Id��Ϣ��execution��ȡ����ʵ����������IdΪkey�ı���ֵ,  
     * ���ݱ���ֵ�ֱ�ִ���������غ���·�е�el���ʽ, ���ҵ�el���ʽͨ������·����û�����
     * @param ActivityImpl activityImpl     ���̽ڵ���Ϣ  
     * @param String activityId             ��ǰ���̽ڵ�Id��Ϣ  
     * @param String elString               ��������˳�����߶��ж�����
     * @param String processInstanceId      ����ʵ��Id��Ϣ  
     * @return  
     */    
    private TaskDefinition nextTaskDefinition(ActivityImpl activityImpl, String activityId, String elString, String processInstanceId){   

        PvmActivity ac = null;

        Object s = null;

        // ��������ڵ�Ϊ�û������ҽڵ㲻�ǵ�ǰ�ڵ���Ϣ
        if ("userTask".equals(activityImpl.getProperty("type")) && !activityId.equals(activityImpl.getId())) {
            // ��ȡ�ýڵ���һ���ڵ���Ϣ
            TaskDefinition taskDefinition = ((UserTaskActivityBehavior) activityImpl.getActivityBehavior())
                    .getTaskDefinition();
            return taskDefinition;
        } else if("exclusiveGateway".equals(activityImpl.getProperty("type"))){// ��ǰ�ڵ�ΪexclusiveGateway
            List<PvmTransition> outTransitions = activityImpl.getOutgoingTransitions();
            // �����������ֻ��һ����·��Ϣ
            if (outTransitions.size() == 1) {
                return nextTaskDefinition((ActivityImpl) outTransitions.get(0).getDestination(), activityId,
                        elString, processInstanceId);
            } else if (outTransitions.size() > 1) { // ������������ж�����·��Ϣ
                for (PvmTransition tr1 : outTransitions) {
                    s = tr1.getProperty("conditionText"); // ��ȡ����������·�ж�������Ϣ
                    // �ж�el���ʽ�Ƿ����
                    if (isCondation(StringUtils.trim(s.toString()))) {
                        return nextTaskDefinition((ActivityImpl) tr1.getDestination(), activityId, elString,
                                processInstanceId);
                    }
                }
            }
        }else {
            // ��ȡ�ڵ�����������·��Ϣ
            List<PvmTransition> outTransitions = activityImpl.getOutgoingTransitions();
            List<PvmTransition> outTransitionsTemp = null;
            for (PvmTransition tr : outTransitions) {
                ac = tr.getDestination(); // ��ȡ��·���յ�ڵ�
                // ���������·Ϊ��������
                if ("exclusiveGateway".equals(ac.getProperty("type"))) {
                    outTransitionsTemp = ac.getOutgoingTransitions();
                    // �����������ֻ��һ����·��Ϣ
                    if (outTransitionsTemp.size() == 1) {
                        return nextTaskDefinition((ActivityImpl) outTransitionsTemp.get(0).getDestination(), activityId,
                                elString, processInstanceId);
                    } else if (outTransitionsTemp.size() > 1) { // ������������ж�����·��Ϣ
                        for (PvmTransition tr1 : outTransitionsTemp) {
                            s = tr1.getProperty("conditionText"); // ��ȡ����������·�ж�������Ϣ
                            // �ж�el���ʽ�Ƿ����
                            if (isCondation(StringUtils.trim(s.toString()))) {
                                return nextTaskDefinition((ActivityImpl) tr1.getDestination(), activityId, elString,processInstanceId);
                            }
                        }
                    }
                } else if ("userTask".equals(ac.getProperty("type"))) {
                    return ((UserTaskActivityBehavior) ((ActivityImpl) ac).getActivityBehavior()).getTaskDefinition();
                } else {
                }
            }
            return null;
        }
        return null;
    }  

    /** 
     * ��ѯ��������ʱ�������������ж�������Ϣ  
     * @param String gatewayId          ��������Id��Ϣ, ��������ʱ��������·���ж�����keyΪ����Id��Ϣ  
     * @param String processInstanceId  ����ʵ��Id��Ϣ  
     * @return 
     */  
    public String getGatewayCondition(String gatewayId, String processInstanceId) {  
        Execution execution = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).singleResult();
        Object object= runtimeService.getVariable(execution.getId(), gatewayId);
        return object==null? "adopt":object.toString();  
    }
    
    /**
     * ��ø���������ʱ����
     * @param processInstanceId
     * @return
     * @Date 2017��12��15��
     * @author qyp
     */
    private Map<String, Object> getAllRunTimeVariables(String processInstanceId) {
    	Execution execution = runtimeService.createExecutionQuery().processInstanceId(processInstanceId).singleResult();
    	Map<String, Object> map = runtimeService.getVariables(execution.getId());
    	return map;
	}

    /** 
     * ����key��value�ж�el���ʽ�Ƿ�ͨ����Ϣ  
     * @param String key    el���ʽkey��Ϣ  
     * @param String el     el���ʽ��Ϣ  
     * @param String value  el���ʽ����ֵ��Ϣ  
     * @return 
     */  
    public boolean isCondition(String key, String el, String value) {  
        ExpressionFactory factory = new ExpressionFactoryImpl();    
        SimpleContext context = new SimpleContext(); 
        context.setVariable(key, factory.createValueExpression(value, String.class));
        ValueExpression e = factory.createValueExpression(context, el, boolean.class);    
        return (Boolean) e.getValue(context);  
    }
    
    /**
     * ��������.
     * (1)����Ĭ��ͨ������(2)����flow����
     * @param el
     * @return
     * @Date 2017��12��15��
     * @author qyp
     */
    private boolean isCondation(String el){
    	if(el.equals("${approval==\"adopt\"}") || el.equals("${approval==\"adjustment\"}")){
    		//ͨ�������ء�����������
    		return true;
    	}else if(el.equals("${approval==\"reject\"}") ||  el.equals("${approval==\"end\"}")) {
    		return false;
		}
    	String checkKey = el.substring(el.indexOf("{")+1, indexOfFirstChar("=",el)).trim();
    	Object value = runTimeVariables.get(checkKey);
    	if(value == null){
    		value = runTimeVariables.get(checkKey.replace("!", ""));
//    		if(!(boolean) value){
//    			value = true;
//    		}
    	}
    	ExpressionFactory factory = new ExpressionFactoryImpl();
    	SimpleContext context = new SimpleContext();
    	if(checkKey.contains("!")){
    		checkKey = checkKey.replace("!", "");
    	}
    	context.setVariable(checkKey, factory.createValueExpression(value, Object.class));
    	ValueExpression e = factory.createValueExpression(context, el, boolean.class);
    	return (Boolean) e.getValue(context);
    }
    
    /**
     * ��ȡcheckStr��str�е�һ�γ��ֵ�λ��
     * @param checkStr
     * @param str
     * @return
     * @Date 2017��12��15��
     * @author qyp
     */
    private int indexOfFirstChar(String checkStr,String str) {
    	Matcher matcher = Pattern.compile(checkStr).matcher(str);  
    	if(matcher.find()){  
    		return matcher.start(); 
    	}else{  
    		return str.length()-1;
    	}  
    }
}
