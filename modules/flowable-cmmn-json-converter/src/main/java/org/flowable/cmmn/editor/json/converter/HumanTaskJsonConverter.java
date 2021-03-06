/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.flowable.cmmn.editor.json.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.flowable.cmmn.editor.json.converter.util.CollectionUtils;
import org.flowable.cmmn.editor.json.model.ModelInfo;
import org.flowable.cmmn.model.BaseElement;
import org.flowable.cmmn.model.CaseElement;
import org.flowable.cmmn.model.CmmnModel;
import org.flowable.cmmn.model.ExtensionElement;
import org.flowable.cmmn.model.HumanTask;
import org.flowable.cmmn.model.PlanItem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * @author Tijs Rademakers
 */
public class HumanTaskJsonConverter extends BaseCmmnJsonConverter implements FormAwareConverter, FormKeyAwareConverter {

    protected Map<String, String> formMap;
    protected Map<String, ModelInfo> formKeyMap;

    public static void fillTypes(Map<String, Class<? extends BaseCmmnJsonConverter>> convertersToBpmnMap, Map<Class<? extends BaseElement>, Class<? extends BaseCmmnJsonConverter>> convertersToJsonMap) {

        fillJsonTypes(convertersToBpmnMap);
        fillBpmnTypes(convertersToJsonMap);
    }

    public static void fillJsonTypes(Map<String, Class<? extends BaseCmmnJsonConverter>> convertersToBpmnMap) {
        convertersToBpmnMap.put(STENCIL_TASK_HUMAN, HumanTaskJsonConverter.class);
    }

    public static void fillBpmnTypes(Map<Class<? extends BaseElement>, Class<? extends BaseCmmnJsonConverter>> convertersToJsonMap) {
        convertersToJsonMap.put(HumanTask.class, HumanTaskJsonConverter.class);
    }

    @Override
    protected String getStencilId(BaseElement baseElement) {
        return STENCIL_TASK_HUMAN;
    }

    @Override
    protected void convertElementToJson(ObjectNode elementNode, ObjectNode propertiesNode, BaseElement baseElement, CmmnModel cmmnModel) {
        PlanItem planItem = (PlanItem) baseElement;
        HumanTask humanTask = (HumanTask) planItem.getPlanItemDefinition();
        String assignee = humanTask.getAssignee();

        if (StringUtils.isNotEmpty(assignee) || CollectionUtils.isNotEmpty(humanTask.getCandidateUsers()) || CollectionUtils.isNotEmpty(humanTask.getCandidateGroups())) {

            ObjectNode assignmentNode = objectMapper.createObjectNode();
            ObjectNode assignmentValuesNode = objectMapper.createObjectNode();

            List<ExtensionElement> idmAssigneeList = humanTask.getExtensionElements().get("flowable-idm-assignee");
            List<ExtensionElement> idmAssigneeFieldList = humanTask.getExtensionElements().get("flowable-idm-assignee-field");
            if (CollectionUtils.isNotEmpty(idmAssigneeList) || CollectionUtils.isNotEmpty(idmAssigneeFieldList)
                    || CollectionUtils.isNotEmpty(humanTask.getExtensionElements().get("flowable-idm-candidate-user"))
                    || CollectionUtils.isNotEmpty(humanTask.getExtensionElements().get("flowable-idm-candidate-group"))) {

                assignmentValuesNode.put("type", "idm");
                ObjectNode idmNode = objectMapper.createObjectNode();
                assignmentValuesNode.set("idm", idmNode);

                List<ExtensionElement> canCompleteList = humanTask.getExtensionElements().get("initiator-can-complete");
                if (CollectionUtils.isNotEmpty(canCompleteList)) {
                    assignmentValuesNode.put("initiatorCanCompleteTask", Boolean.valueOf(canCompleteList.get(0).getElementText()));
                }

                if (StringUtils.isNotEmpty(humanTask.getAssignee())) {
                    ObjectNode assigneeNode = objectMapper.createObjectNode();
                    if (humanTask.getAssignee().contains("${taskAssignmentBean.assignTaskToAssignee(")) {
                        idmNode.set("assigneeField", assigneeNode);
                        idmNode.put("type", "user");

                        fillProperty("id", "flowable-idm-assignee-field", assigneeNode, humanTask);
                        fillProperty("name", "assignee-field-info-name", assigneeNode, humanTask);

                    } else {
                        assigneeNode.put("id", humanTask.getAssignee());
                        idmNode.set("assignee", assigneeNode);
                        idmNode.put("type", "user");

                        fillProperty("externalId", "assignee-info-externalid", assigneeNode, humanTask);
                        fillProperty("email", "assignee-info-email", assigneeNode, humanTask);
                        fillProperty("firstName", "assignee-info-firstname", assigneeNode, humanTask);
                        fillProperty("lastName", "assignee-info-lastname", assigneeNode, humanTask);
                    }
                }

                List<ExtensionElement> idmCandidateUserList = humanTask.getExtensionElements().get("flowable-idm-candidate-user");
                if (CollectionUtils.isNotEmpty(humanTask.getCandidateUsers()) && CollectionUtils.isNotEmpty(idmCandidateUserList)) {

                    List<String> candidateUserIds = new ArrayList<>();

                    if (humanTask.getCandidateUsers().size() == 1 && humanTask.getCandidateUsers().get(0).contains("${taskAssignmentBean.assignTaskToCandidateUsers(")) {
                        idmNode.put("type", "users");

                        String candidateUsersString = humanTask.getCandidateUsers().get(0);
                        candidateUsersString = candidateUsersString.replace("${taskAssignmentBean.assignTaskToCandidateUsers('", "");
                        candidateUsersString = candidateUsersString.replace("', execution)}", "");

                        List<String> candidateFieldIds = new ArrayList<>();

                        String[] candidateUserArray = candidateUsersString.split(",");
                        for (String candidate : candidateUserArray) {
                            if (candidate.contains("field(")) {
                                candidateFieldIds.add(candidate.trim().substring(6, candidate.length() - 1));
                            } else {
                                candidateUserIds.add(candidate.trim());
                            }
                        }

                        if (candidateFieldIds.size() > 0) {
                            ArrayNode candidateUserFieldsNode = objectMapper.createArrayNode();
                            idmNode.set("candidateUserFields", candidateUserFieldsNode);
                            for (String fieldId : candidateFieldIds) {
                                ObjectNode fieldNode = objectMapper.createObjectNode();
                                fieldNode.put("id", fieldId);
                                candidateUserFieldsNode.add(fieldNode);

                                fillProperty("name", "user-field-info-name-" + fieldId, fieldNode, humanTask);
                            }
                        }

                    } else {
                        candidateUserIds.addAll(humanTask.getCandidateUsers());
                    }

                    if (candidateUserIds.size() > 0) {
                        ArrayNode candidateUsersNode = objectMapper.createArrayNode();
                        idmNode.set("candidateUsers", candidateUsersNode);
                        idmNode.put("type", "users");
                        for (String candidateUser : candidateUserIds) {
                            ObjectNode candidateUserNode = objectMapper.createObjectNode();
                            candidateUserNode.put("id", candidateUser);
                            candidateUsersNode.add(candidateUserNode);

                            fillProperty("externalId", "user-info-externalid-" + candidateUser, candidateUserNode, humanTask);
                            fillProperty("email", "user-info-email-" + candidateUser, candidateUserNode, humanTask);
                            fillProperty("firstName", "user-info-firstname-" + candidateUser, candidateUserNode, humanTask);
                            fillProperty("lastName", "user-info-lastname-" + candidateUser, candidateUserNode, humanTask);
                        }
                    }
                }

                List<ExtensionElement> idmCandidateGroupList = humanTask.getExtensionElements().get("flowable-idm-candidate-group");
                if (CollectionUtils.isNotEmpty(humanTask.getCandidateGroups()) && CollectionUtils.isNotEmpty(idmCandidateGroupList)) {

                    List<String> candidateGroupIds = new ArrayList<>();

                    if (humanTask.getCandidateGroups().size() == 1 && humanTask.getCandidateGroups().get(0).contains("${taskAssignmentBean.assignTaskToCandidateGroups(")) {
                        idmNode.put("type", "groups");

                        String candidateGroupsString = humanTask.getCandidateGroups().get(0);
                        candidateGroupsString = candidateGroupsString.replace("${taskAssignmentBean.assignTaskToCandidateGroups('", "");
                        candidateGroupsString = candidateGroupsString.replace("', execution)}", "");

                        List<String> candidateFieldIds = new ArrayList<>();

                        String[] candidateGroupArray = candidateGroupsString.split(",");
                        for (String candidate : candidateGroupArray) {
                            if (candidate.contains("field(")) {
                                candidateFieldIds.add(candidate.trim().substring(6, candidate.length() - 1));
                            } else {
                                candidateGroupIds.add(candidate.trim());
                            }
                        }

                        if (candidateFieldIds.size() > 0) {
                            ArrayNode candidateGroupFieldsNode = objectMapper.createArrayNode();
                            idmNode.set("candidateGroupFields", candidateGroupFieldsNode);
                            for (String fieldId : candidateFieldIds) {
                                ObjectNode fieldNode = objectMapper.createObjectNode();
                                fieldNode.put("id", fieldId);
                                candidateGroupFieldsNode.add(fieldNode);

                                fillProperty("name", "group-field-info-name-" + fieldId, fieldNode, humanTask);
                            }
                        }

                    } else {
                        candidateGroupIds.addAll(humanTask.getCandidateGroups());
                    }

                    if (candidateGroupIds.size() > 0) {
                        ArrayNode candidateGroupsNode = objectMapper.createArrayNode();
                        idmNode.set("candidateGroups", candidateGroupsNode);
                        idmNode.put("type", "groups");
                        for (String candidateGroup : candidateGroupIds) {
                            ObjectNode candidateGroupNode = objectMapper.createObjectNode();
                            candidateGroupNode.put("id", candidateGroup);
                            candidateGroupsNode.add(candidateGroupNode);

                            fillProperty("externalId", "group-info-externalid-" + candidateGroup, candidateGroupNode, humanTask);
                            fillProperty("name", "group-info-name-" + candidateGroup, candidateGroupNode, humanTask);
                        }
                    }
                }

            } else {
                assignmentValuesNode.put("type", "static");

                if (StringUtils.isNotEmpty(assignee)) {
                    assignmentValuesNode.put(PROPERTY_USERTASK_ASSIGNEE, assignee);
                }

                if (CollectionUtils.isNotEmpty(humanTask.getCandidateUsers())) {
                    ArrayNode candidateArrayNode = objectMapper.createArrayNode();
                    for (String candidateUser : humanTask.getCandidateUsers()) {
                        ObjectNode candidateNode = objectMapper.createObjectNode();
                        candidateNode.put("value", candidateUser);
                        candidateArrayNode.add(candidateNode);
                    }
                    assignmentValuesNode.set(PROPERTY_USERTASK_CANDIDATE_USERS, candidateArrayNode);
                }

                if (CollectionUtils.isNotEmpty(humanTask.getCandidateGroups())) {
                    ArrayNode candidateArrayNode = objectMapper.createArrayNode();
                    for (String candidateGroup : humanTask.getCandidateGroups()) {
                        ObjectNode candidateNode = objectMapper.createObjectNode();
                        candidateNode.put("value", candidateGroup);
                        candidateArrayNode.add(candidateNode);
                    }
                    assignmentValuesNode.set(PROPERTY_USERTASK_CANDIDATE_GROUPS, candidateArrayNode);
                }
            }

            assignmentNode.set("assignment", assignmentValuesNode);
            propertiesNode.set(PROPERTY_USERTASK_ASSIGNMENT, assignmentNode);
        }

        if (humanTask.getPriority() != null) {
            setPropertyValue(PROPERTY_USERTASK_PRIORITY, humanTask.getPriority(), propertiesNode);
        }

        if (StringUtils.isNotEmpty(humanTask.getFormKey())) {
            if (formKeyMap != null && formKeyMap.containsKey(humanTask.getFormKey())) {
                ObjectNode formRefNode = objectMapper.createObjectNode();
                ModelInfo modelInfo = formKeyMap.get(humanTask.getFormKey());
                formRefNode.put("id", modelInfo.getId());
                formRefNode.put("name", modelInfo.getName());
                formRefNode.put("key", modelInfo.getKey());
                propertiesNode.set(PROPERTY_FORM_REFERENCE, formRefNode);

            } else {
                setPropertyValue(PROPERTY_FORMKEY, humanTask.getFormKey(), propertiesNode);
            }
        }

        setPropertyValue(PROPERTY_USERTASK_DUEDATE, humanTask.getDueDate(), propertiesNode);
        setPropertyValue(PROPERTY_USERTASK_CATEGORY, humanTask.getCategory(), propertiesNode);
    }

    protected int getExtensionElementValueAsInt(String name, HumanTask humanTask) {
        int intValue = 0;
        String value = getExtensionElementValue(name, humanTask);
        if (value != null && NumberUtils.isNumber(value)) {
            intValue = Integer.valueOf(value);
        }
        return intValue;
    }

    protected String getExtensionElementValue(String name, HumanTask humanTask) {
        String value = "";
        if (CollectionUtils.isNotEmpty(humanTask.getExtensionElements().get(name))) {
            ExtensionElement extensionElement = humanTask.getExtensionElements().get(name).get(0);
            value = extensionElement.getElementText();
        }
        return value;
    }

    @Override
    protected CaseElement convertJsonToElement(JsonNode elementNode, JsonNode modelNode, BaseElement parentElement, Map<String, JsonNode> shapeMap, CmmnModel cmmnModel) {
        HumanTask task = new HumanTask();

        task.setPriority(getPropertyValueAsString(PROPERTY_USERTASK_PRIORITY, elementNode));
        String formKey = getPropertyValueAsString(PROPERTY_FORMKEY, elementNode);
        if (StringUtils.isNotEmpty(formKey)) {
            task.setFormKey(formKey);
        } else {
            JsonNode formReferenceNode = getProperty(PROPERTY_FORM_REFERENCE, elementNode);
            if (formReferenceNode != null && formReferenceNode.get("id") != null) {

                if (formMap != null && formMap.containsKey(formReferenceNode.get("id").asText())) {
                    task.setFormKey(formMap.get(formReferenceNode.get("id").asText()));
                }
            }
        }

        task.setDueDate(getPropertyValueAsString(PROPERTY_USERTASK_DUEDATE, elementNode));
        task.setCategory(getPropertyValueAsString(PROPERTY_USERTASK_CATEGORY, elementNode));

        JsonNode assignmentNode = getProperty(PROPERTY_USERTASK_ASSIGNMENT, elementNode);
        if (assignmentNode != null) {
            JsonNode assignmentDefNode = assignmentNode.get("assignment");
            if (assignmentDefNode != null) {

                JsonNode typeNode = assignmentDefNode.get("type");
                JsonNode canCompleteTaskNode = assignmentDefNode.get("initiatorCanCompleteTask");
                if (typeNode == null || "static".equalsIgnoreCase(typeNode.asText())) {
                    JsonNode assigneeNode = assignmentDefNode.get(PROPERTY_USERTASK_ASSIGNEE);
                    if (assigneeNode != null && !assigneeNode.isNull()) {
                        task.setAssignee(assigneeNode.asText());
                    }

                    task.setCandidateUsers(getValueAsList(PROPERTY_USERTASK_CANDIDATE_USERS, assignmentDefNode));
                    task.setCandidateGroups(getValueAsList(PROPERTY_USERTASK_CANDIDATE_GROUPS, assignmentDefNode));

                    if (StringUtils.isNotEmpty(task.getAssignee()) && !"$INITIATOR".equalsIgnoreCase(task.getAssignee())) {

                        if (canCompleteTaskNode != null && !canCompleteTaskNode.isNull()) {
                            addInitiatorCanCompleteExtensionElement(Boolean.valueOf(canCompleteTaskNode.asText()), task);
                        } else {
                            addInitiatorCanCompleteExtensionElement(false, task);
                        }

                    } else if (StringUtils.isNotEmpty(task.getAssignee()) && "$INITIATOR".equalsIgnoreCase(task.getAssignee())) {
                        addInitiatorCanCompleteExtensionElement(true, task);
                    }

                } else if ("idm".equalsIgnoreCase(typeNode.asText())) {
                    JsonNode idmDefNode = assignmentDefNode.get("idm");
                    if (idmDefNode != null && idmDefNode.has("type")) {
                        JsonNode idmTypeNode = idmDefNode.get("type");
                        if (idmTypeNode != null && "user".equalsIgnoreCase(idmTypeNode.asText()) && (idmDefNode.has("assignee") || idmDefNode.has("assigneeField"))) {

                            fillAssigneeInfo(idmDefNode, canCompleteTaskNode, task);

                        } else if (idmTypeNode != null && "users".equalsIgnoreCase(idmTypeNode.asText()) && (idmDefNode.has("candidateUsers") || idmDefNode.has("candidateUserFields"))) {

                            fillCandidateUsers(idmDefNode, canCompleteTaskNode, task);

                        } else if (idmTypeNode != null && "groups".equalsIgnoreCase(idmTypeNode.asText()) && (idmDefNode.has("candidateGroups") || idmDefNode.has("candidateGroupFields"))) {

                            fillCandidateGroups(idmDefNode, canCompleteTaskNode, task);

                        } else {
                            task.setAssignee("$INITIATOR");
                            addExtensionElement("flowable-idm-initiator", String.valueOf(true), task);
                        }
                    }
                }
            }
        }
        
        return task;
    }

    protected void fillAssigneeInfo(JsonNode idmDefNode, JsonNode canCompleteTaskNode, HumanTask task) {
        JsonNode assigneeNode = idmDefNode.get("assignee");
        JsonNode assigneeFieldNode = idmDefNode.get("assigneeField");

        if (assigneeNode != null && !assigneeNode.isNull()) {
            JsonNode idNode = assigneeNode.get("id");
            JsonNode emailNode = assigneeNode.get("email");
            if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                task.setAssignee(idNode.asText());
                addExtensionElement("flowable-idm-assignee", String.valueOf(true), task);
                addExtensionElement("assignee-info-email", emailNode, task);
                addExtensionElement("assignee-info-firstname", assigneeNode.get("firstName"), task);
                addExtensionElement("assignee-info-lastname", assigneeNode.get("lastName"), task);
                addExtensionElement("assignee-info-externalid", assigneeNode.get("externalId"), task);

            } else if (emailNode != null && !emailNode.isNull() && StringUtils.isNotEmpty(emailNode.asText())) {
                task.setAssignee(emailNode.asText());

                // The email is added as extension element. Later (eg on deploy) the assignee
                // is replaced by a real user id, but the email information kept in this extension element
                addExtensionElement("flowable-assignee-email", task.getAssignee(), task);
                addExtensionElement("flowable-idm-assignee", String.valueOf(true), task);
            }

        } else if (assigneeFieldNode != null && !assigneeFieldNode.isNull()) {
            JsonNode idNode = assigneeFieldNode.get("id");
            if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                task.setAssignee("${taskAssignmentBean.assignTaskToAssignee('" + idNode.asText() + "', execution)}");
                addExtensionElement("flowable-idm-assignee-field", idNode.asText(), task);
                addExtensionElement("assignee-field-info-name", assigneeFieldNode.get("name"), task);
            }
        }

        if (canCompleteTaskNode != null && !canCompleteTaskNode.isNull()) {
            addInitiatorCanCompleteExtensionElement(Boolean.valueOf(canCompleteTaskNode.asText()), task);
        } else {
            addInitiatorCanCompleteExtensionElement(false, task);
        }
    }

    protected void fillCandidateUsers(JsonNode idmDefNode, JsonNode canCompleteTaskNode, HumanTask task) {
        List<String> candidateUsers = new ArrayList<>();
        JsonNode candidateUsersNode = idmDefNode.get("candidateUsers");
        if (candidateUsersNode != null && candidateUsersNode.isArray()) {
            List<String> emails = new ArrayList<>();
            for (JsonNode userNode : candidateUsersNode) {
                if (userNode != null && !userNode.isNull()) {
                    JsonNode idNode = userNode.get("id");
                    JsonNode emailNode = userNode.get("email");
                    if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                        String id = idNode.asText();
                        candidateUsers.add(id);

                        addExtensionElement("user-info-email-" + id, emailNode, task);
                        addExtensionElement("user-info-firstname-" + id, userNode.get("firstName"), task);
                        addExtensionElement("user-info-lastname-" + id, userNode.get("lastName"), task);
                        addExtensionElement("user-info-externalid-" + id, userNode.get("externalId"), task);

                    } else if (emailNode != null && !emailNode.isNull() && StringUtils.isNotEmpty(emailNode.asText())) {
                        String email = emailNode.asText();
                        candidateUsers.add(email);
                        emails.add(email);
                    }
                }
            }

            if (emails.size() > 0) {
                // Email extension element
                addExtensionElement("flowable-candidate-users-emails", StringUtils.join(emails, ","), task);
            }

            if (candidateUsers.size() > 0) {
                addExtensionElement("flowable-idm-candidate-user", String.valueOf(true), task);
                if (canCompleteTaskNode != null && !canCompleteTaskNode.isNull()) {
                    addInitiatorCanCompleteExtensionElement(Boolean.valueOf(canCompleteTaskNode.asText()), task);
                } else {
                    addInitiatorCanCompleteExtensionElement(false, task);
                }
            }
        }

        JsonNode candidateUserFieldsNode = idmDefNode.get("candidateUserFields");
        if (candidateUserFieldsNode != null && candidateUserFieldsNode.isArray()) {
            for (JsonNode fieldNode : candidateUserFieldsNode) {
                JsonNode idNode = fieldNode.get("id");
                if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                    String id = idNode.asText();
                    candidateUsers.add("field(" + id + ")");

                    addExtensionElement("user-field-info-name-" + id, fieldNode.get("name"), task);
                }
            }
        }

        if (candidateUsers.size() > 0) {
            if (candidateUserFieldsNode != null && candidateUserFieldsNode.isArray() && candidateUserFieldsNode.size() > 0) {
                String candidateUsersString = StringUtils.join(candidateUsers, ",");
                candidateUsersString = "${taskAssignmentBean.assignTaskToCandidateUsers('" + candidateUsersString + "', execution)}";
                candidateUsers.clear();
                candidateUsers.add(candidateUsersString);
                task.setCandidateUsers(candidateUsers);

            } else {
                task.setCandidateUsers(candidateUsers);
            }
        }
    }

    protected void fillCandidateGroups(JsonNode idmDefNode, JsonNode canCompleteTaskNode, HumanTask task) {
        List<String> candidateGroups = new ArrayList<>();
        JsonNode candidateGroupsNode = idmDefNode.get("candidateGroups");
        if (candidateGroupsNode != null && candidateGroupsNode.isArray()) {
            for (JsonNode groupNode : candidateGroupsNode) {
                if (groupNode != null && !groupNode.isNull()) {
                    JsonNode idNode = groupNode.get("id");
                    JsonNode nameNode = groupNode.get("name");
                    if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                        String id = idNode.asText();
                        candidateGroups.add(id);

                        addExtensionElement("group-info-name-" + id, nameNode, task);
                        addExtensionElement("group-info-externalid-" + id, groupNode.get("externalId"), task);
                    }
                }
            }
        }

        JsonNode candidateGroupFieldsNode = idmDefNode.get("candidateGroupFields");
        if (candidateGroupFieldsNode != null && candidateGroupFieldsNode.isArray()) {
            for (JsonNode fieldNode : candidateGroupFieldsNode) {
                JsonNode idNode = fieldNode.get("id");
                if (idNode != null && !idNode.isNull() && StringUtils.isNotEmpty(idNode.asText())) {
                    String id = idNode.asText();
                    candidateGroups.add("field(" + id + ")");

                    addExtensionElement("group-field-info-name-" + id, fieldNode.get("name"), task);
                }
            }
        }

        if (candidateGroups.size() > 0) {
            if (candidateGroupFieldsNode != null && candidateGroupFieldsNode.isArray() && candidateGroupFieldsNode.size() > 0) {
                String candidateGroupsString = StringUtils.join(candidateGroups, ",");
                candidateGroupsString = "${taskAssignmentBean.assignTaskToCandidateGroups('" + candidateGroupsString + "', execution)}";
                candidateGroups.clear();
                candidateGroups.add(candidateGroupsString);
                task.setCandidateGroups(candidateGroups);

            } else {
                task.setCandidateGroups(candidateGroups);
            }

            addExtensionElement("flowable-idm-candidate-group", String.valueOf(true), task);
            if (canCompleteTaskNode != null && !canCompleteTaskNode.isNull()) {
                addInitiatorCanCompleteExtensionElement(Boolean.valueOf(canCompleteTaskNode.asText()), task);
            } else {
                addInitiatorCanCompleteExtensionElement(false, task);
            }
        }
    }

    protected void addInitiatorCanCompleteExtensionElement(boolean canCompleteTask, HumanTask task) {
        addExtensionElement("initiator-can-complete", String.valueOf(canCompleteTask), task);
    }

    protected void addExtensionElement(String name, JsonNode elementNode, HumanTask task) {
        if (elementNode != null && !elementNode.isNull() && StringUtils.isNotEmpty(elementNode.asText())) {
            addExtensionElement(name, elementNode.asText(), task);
        }
    }

    protected void addExtensionElement(String name, String elementText, HumanTask task) {
        ExtensionElement extensionElement = new ExtensionElement();
        extensionElement.setNamespace(NAMESPACE);
        extensionElement.setNamespacePrefix("modeler");
        extensionElement.setName(name);
        extensionElement.setElementText(elementText);
        task.addExtensionElement(extensionElement);
    }

    protected void fillProperty(String propertyName, String extensionElementName, ObjectNode elementNode, HumanTask task) {
        List<ExtensionElement> extensionElementList = task.getExtensionElements().get(extensionElementName);
        if (CollectionUtils.isNotEmpty(extensionElementList)) {
            elementNode.put(propertyName, extensionElementList.get(0).getElementText());
        }
    }

    @Override
    public void setFormMap(Map<String, String> formMap) {
        this.formMap = formMap;
    }

    @Override
    public void setFormKeyMap(Map<String, ModelInfo> formKeyMap) {
        this.formKeyMap = formKeyMap;
    }
}
