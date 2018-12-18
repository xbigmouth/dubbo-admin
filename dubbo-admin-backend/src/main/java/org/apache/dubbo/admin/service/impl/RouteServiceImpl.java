/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.admin.service.impl;

import org.apache.dubbo.admin.common.exception.ResourceNotFoundException;
import org.apache.dubbo.admin.common.util.Constants;
import org.apache.dubbo.admin.common.util.ConvertUtil;
import org.apache.dubbo.admin.common.util.RouteRule;
import org.apache.dubbo.admin.common.util.YamlParser;
import org.apache.dubbo.admin.model.domain.Route;
import org.apache.dubbo.admin.model.dto.AccessDTO;
import org.apache.dubbo.admin.model.dto.ConditionRouteDTO;
import org.apache.dubbo.admin.model.dto.TagRouteDTO;
import org.apache.dubbo.admin.model.store.BlackWhiteList;
import org.apache.dubbo.admin.model.store.RoutingRuleDTO;
import org.apache.dubbo.admin.service.RouteService;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.utils.StringUtils;
import org.springframework.stereotype.Component;

import java.time.YearMonth;
import java.util.List;

/**
 * IbatisRouteService
 *
 */
@Component
public class RouteServiceImpl extends AbstractService implements RouteService {

    private String prefix = Constants.CONFIG_KEY;

    @Override
    public void createConditionRoute(ConditionRouteDTO conditionRoute) {
        String id = ConvertUtil.getIdFromDTO(conditionRoute);
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String existConfig = dynamicConfiguration.getConfig(path);
        RoutingRuleDTO existRule = null;
        if (existConfig != null) {
            existRule = YamlParser.loadObject(existConfig, RoutingRuleDTO.class);
        }
        existRule = RouteRule.insertConditionRule(existRule, conditionRoute);
        //register2.7
        dynamicConfiguration.setConfig(path, YamlParser.dumpObject(existRule));

        //register2.6
        if (StringUtils.isNotEmpty(conditionRoute.getService())) {
            Route old = convertRouteToOldRoute(conditionRoute);
            registry.register(old.toUrl().addParameter(Constants.COMPATIBLE_CONFIG, true));
        }

    }

    @Override
    public void updateConditionRoute(ConditionRouteDTO newConditionRoute) {
        String id = ConvertUtil.getIdFromDTO(newConditionRoute);
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String existConfig = dynamicConfiguration.getConfig(path);
        if (existConfig == null) {
            throw new ResourceNotFoundException("no existing condition route for path: " + path);
        }
        RoutingRuleDTO routingRuleDTO = YamlParser.loadObject(existConfig, RoutingRuleDTO.class);
        ConditionRouteDTO oldConditionRoute = RouteRule.createConditionRouteFromRule(routingRuleDTO);
        routingRuleDTO = RouteRule.insertConditionRule(routingRuleDTO, newConditionRoute);
        dynamicConfiguration.setConfig(path, YamlParser.dumpObject(routingRuleDTO));

        //for 2.6
        if (StringUtils.isNotEmpty(newConditionRoute.getService())) {
            Route old = convertRouteToOldRoute(oldConditionRoute);
            Route updated = convertRouteToOldRoute(newConditionRoute);
            registry.unregister(old.toUrl().addParameter(Constants.COMPATIBLE_CONFIG, true));
            registry.register(updated.toUrl().addParameter(Constants.COMPATIBLE_CONFIG, true));
        }
    }

    @Override
    public void deleteConditionRoute(String id) {
        if (StringUtils.isEmpty(id)) {
            // throw exception
        }
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config == null) {
            //throw exception
        }
        RoutingRuleDTO route = YamlParser.loadObject(config, RoutingRuleDTO.class);
        if (route.getBlackWhiteList() != null) {
           route.setConditions(null);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(route));
        } else {
            dynamicConfiguration.deleteConfig(path);
        }

        //for 2.6
        if (route.getScope().equals(Constants.SERVICE)) {
            RoutingRuleDTO originRule = YamlParser.loadObject(config, RoutingRuleDTO.class);
            ConditionRouteDTO conditionRouteDTO = RouteRule.createConditionRouteFromRule(originRule);
            Route old = convertRouteToOldRoute(conditionRouteDTO);
            registry.unregister(old.toUrl().addParameter(Constants.COMPATIBLE_CONFIG, true));
        }
    }

    @Override
    public void deleteAccess(String id) {
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO ruleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);
            BlackWhiteList old = ruleDTO.getBlackWhiteList();
            if (ruleDTO.getConditions() == null) {
                dynamicConfiguration.deleteConfig(path);
            } else {
                ruleDTO.setBlackWhiteList(null);
                dynamicConfiguration.setConfig(path, YamlParser.dumpObject(ruleDTO));
            }
            //2.6
            if (ruleDTO.getScope().equals(Constants.SERVICE) && old != null) {
                Route route = RouteRule.convertBlackWhiteListtoRoute(old, Constants.SERVICE, id);
                registry.unregister(route.toUrl());
            }
        }
    }

    @Override
    public void createAccess(AccessDTO accessDTO) {
        String id = ConvertUtil.getIdFromDTO(accessDTO);
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        BlackWhiteList blackWhiteList = RouteRule.convertToBlackWhiteList(accessDTO);
        RoutingRuleDTO ruleDTO;
        if (config == null) {
            ruleDTO = new RoutingRuleDTO();
            ruleDTO.setEnabled(true);
            if (StringUtils.isNoneEmpty(accessDTO.getApplication())) {
                ruleDTO.setScope(Constants.APPLICATION);
            } else {
                ruleDTO.setScope(Constants.SERVICE);
            }
            ruleDTO.setKey(id);
            ruleDTO.setBlackWhiteList(blackWhiteList);
        } else {
            ruleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);
            if (ruleDTO.getBlackWhiteList() != null) {
                //todo throw exception
            }
            ruleDTO.setBlackWhiteList(blackWhiteList);
        }
        dynamicConfiguration.setConfig(path, YamlParser.dumpObject(ruleDTO));

        //for 2.6
        if (ruleDTO.getScope().equals("service")) {
            Route route = RouteRule.convertAccessDTOtoRoute(accessDTO);
            registry.register(route.toUrl());
        }

    }

    @Override
    public AccessDTO findAccess(String id) {
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO ruleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);
            BlackWhiteList blackWhiteList = ruleDTO.getBlackWhiteList();
            return RouteRule.convertToAccessDTO(blackWhiteList, ruleDTO.getScope(), ruleDTO.getKey());
        }
        return null;
    }

    @Override
    public void updateAccess(AccessDTO accessDTO) {
        String key = ConvertUtil.getIdFromDTO(accessDTO);
        String path = getPath(key, Constants.CONDITION_ROUTE);
        BlackWhiteList blackWhiteList = RouteRule.convertToBlackWhiteList(accessDTO);
        BlackWhiteList old = null;
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO ruleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);
            old = ruleDTO.getBlackWhiteList();
            ruleDTO.setBlackWhiteList(blackWhiteList);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(ruleDTO));
        }

        //2.6
        if (StringUtils.isNotEmpty(accessDTO.getService())) {
            Route oldRoute = RouteRule.convertBlackWhiteListtoRoute(old, Constants.SERVICE, key);
            Route newRoute = RouteRule.convertAccessDTOtoRoute(accessDTO);
            registry.unregister(oldRoute.toUrl());
            registry.register(newRoute.toUrl());
        }
    }

    @Override
    public void enableConditionRoute(String id) {
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO ruleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);

            if (ruleDTO.getScope().equals(Constants.SERVICE)) {
                //for2.6
                URL oldURL = convertRouteToOldRoute(RouteRule.createConditionRouteFromRule(ruleDTO)).toUrl().addParameter(Constants.COMPATIBLE_CONFIG, true);
                registry.unregister(oldURL);
                oldURL = oldURL.addParameter("enabled", true);
                registry.register(oldURL);
            }

            //2.7
            ruleDTO.setEnabled(true);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(ruleDTO));
        }

    }

    @Override
    public void disableConditionRoute(String serviceName) {
        String path = getPath(serviceName, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO routeRule = YamlParser.loadObject(config, RoutingRuleDTO.class);

            if (routeRule.getScope().equals(Constants.SERVICE)) {
                //for 2.6
                URL oldURL = convertRouteToOldRoute(RouteRule.createConditionRouteFromRule(routeRule)).toUrl().addParameter(Constants.COMPATIBLE_CONFIG,true);
                registry.unregister(oldURL);
                oldURL = oldURL.addParameter("enabled", false);
                registry.register(oldURL);
            }

            //2.7
            routeRule.setEnabled(false);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(routeRule));
        }

    }

    @Override
    public ConditionRouteDTO findConditionRoute(String id) {
        String path = getPath(id, Constants.CONDITION_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            RoutingRuleDTO routingRuleDTO = YamlParser.loadObject(config, RoutingRuleDTO.class);
            ConditionRouteDTO conditionRouteDTO = RouteRule.createConditionRouteFromRule(routingRuleDTO);
            return conditionRouteDTO;
        }
        return null;
    }

    @Override
    public void createTagRoute(TagRouteDTO tagRoute) {
        String id = ConvertUtil.getIdFromDTO(tagRoute);
        String path = getPath(id,Constants.TAG_ROUTE);
        dynamicConfiguration.setConfig(path, YamlParser.dumpObject(tagRoute));
    }

    @Override
    public void updateTagRoute(TagRouteDTO tagRoute) {
        String id = ConvertUtil.getIdFromDTO(tagRoute);
        String path = getPath(id, Constants.TAG_ROUTE);
        if (dynamicConfiguration.getConfig(path) == null) {
            throw new ResourceNotFoundException("can not find tagroute: " + id);
            //throw exception
        }
        dynamicConfiguration.setConfig(path, YamlParser.dumpObject(tagRoute));

    }

    @Override
    public void deleteTagRoute(String id) {
        String path = getPath(id, Constants.TAG_ROUTE);
        dynamicConfiguration.deleteConfig(path);
    }

    @Override
    public void enableTagRoute(String id) {
        String path = getPath(id, Constants.TAG_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            TagRouteDTO tagRoute = YamlParser.loadObject(config, TagRouteDTO.class);
            tagRoute.setEnabled(true);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(tagRoute));
        }

    }

    @Override
    public void disableTagRoute(String id) {
        String path = getPath(id, Constants.TAG_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            TagRouteDTO tagRoute = YamlParser.loadObject(config, TagRouteDTO.class);
            tagRoute.setEnabled(false);
            dynamicConfiguration.setConfig(path, YamlParser.dumpObject(tagRoute));
        }

    }

    @Override
    public TagRouteDTO findTagRoute(String id) {
        String path = getPath(id, Constants.TAG_ROUTE);
        String config = dynamicConfiguration.getConfig(path);
        if (config != null) {
            return YamlParser.loadObject(config, TagRouteDTO.class);
        }
        return null;
    }

    private String getPath(String key, String type) {
        if (type.equals(Constants.CONDITION_ROUTE)) {
            return prefix + Constants.PATH_SEPARATOR + key + Constants.PATH_SEPARATOR + "routers";
        } else {
            return prefix + Constants.PATH_SEPARATOR + key + Constants.PATH_SEPARATOR + "tagrouters";
        }
    }

    private String parseCondition(List<String> conditions) {
        StringBuilder when = new StringBuilder();
        StringBuilder then = new StringBuilder();
        for (String condition : conditions) {
            condition = condition.trim();
            if (condition.contains("=>")) {
                String[] array = condition.split("=>", 2);
                String consumer = array[0].trim();
                String provider = array[1].trim();
                if (consumer != "") {
                    if (when.length() != 0) {
                        when.append(" & ").append(consumer);
                    } else {
                        when.append(consumer);
                    }
                }
                if (provider != "") {
                    if (then.length() != 0) {
                        then.append(" & ").append(provider);
                    } else {
                        then.append(provider);
                    }
                }
            }
        }
        return (when.append(" => ").append(then)).toString();
    }

    private Route convertRouteToOldRoute(ConditionRouteDTO route) {
        Route old = new Route();
        old.setService(route.getService());
        old.setEnabled(route.isEnabled());
        old.setForce(route.isForce());
        old.setRuntime(route.isRuntime());
        old.setPriority(route.getPriority());
        String rule = parseCondition(route.getConditions());
        old.setRule(rule);
        return old;
    }
}
