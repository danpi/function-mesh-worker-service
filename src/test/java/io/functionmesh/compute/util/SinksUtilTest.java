/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.functionmesh.compute.util;

import com.google.gson.Gson;
import io.functionmesh.compute.MeshWorkerService;
import io.functionmesh.compute.functions.models.V1alpha1FunctionSpecPodEnv;
import io.functionmesh.compute.models.CustomRuntimeOptions;
import io.functionmesh.compute.models.FunctionMeshConnectorDefinition;
import io.functionmesh.compute.models.MeshWorkerServiceCustomConfig;
import io.functionmesh.compute.sinks.models.V1alpha1Sink;
import io.functionmesh.compute.sinks.models.V1alpha1SinkSpec;
import io.functionmesh.compute.sinks.models.V1alpha1SinkSpecPodEnv;
import io.functionmesh.compute.testdata.Generate;
import io.functionmesh.compute.worker.MeshConnectorsManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.io.FileUtils;
import org.apache.pulsar.common.io.SinkConfig;
import org.apache.pulsar.common.nar.NarClassLoader;
import org.apache.pulsar.functions.utils.FunctionCommon;
import org.apache.pulsar.functions.utils.io.ConnectorUtils;
import org.apache.pulsar.functions.worker.WorkerConfig;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

@RunWith(PowerMockRunner.class)
@PrepareForTest({FunctionCommon.class, ConnectorUtils.class, FileUtils.class})
@PowerMockIgnore({"javax.management.*"})
public class SinksUtilTest {
    private final String kind = "Sink";
    private final String plural = "functions";
    private final String group = "compute.functionmesh.io";
    private final String version = "v1alpha1";

    @Test
    public void testCreateV1alpha1SinkFromSinkConfig()
            throws ClassNotFoundException, IOException, URISyntaxException {
        String tenant = "public";
        String namespace = "default";
        String componentName = "sink-es-sample";
        String className = "org.apache.pulsar.io.elasticsearch.ElasticSearchSink";
        String inputTopic = "persistent://public/default/input";
        String typeClassName = "[B";
        String archive = "connectors/pulsar-io-elastic-search-2.7.0-rc-pm-3.nar";
        String jar = "/pulsar/pulsar-io-elastic-search-2.7.0-rc-pm-3.nar";
        boolean autoAck = true;
        int parallelism = 1;
        String clusterName = "test-pulsar";
        Map<String, Object> configs = new HashMap<>();
        configs.put("elasticSearchUrl", "https://testing-es.app");
        File narFile = PowerMockito.mock(File.class);
        PowerMockito.when(narFile.getPath()).thenReturn("");
        FileInputStream uploadedInputStream = PowerMockito.mock(FileInputStream.class);

        NarClassLoader narClassLoader = PowerMockito.mock(NarClassLoader.class);
        PowerMockito.when(narClassLoader.loadClass(className)).thenReturn(null);
        PowerMockito.mockStatic(FunctionCommon.class);
        PowerMockito.mockStatic(ConnectorUtils.class);
        PowerMockito.mockStatic(FileUtils.class);
        PowerMockito.when(FunctionCommon.extractNarClassLoader(narFile, null))
                .thenReturn(narClassLoader);
        PowerMockito.when(FunctionCommon.createPkgTempFile()).thenReturn(narFile);
        PowerMockito.when(ConnectorUtils.getIOSinkClass(narClassLoader)).thenReturn(className);
        PowerMockito.<Class<?>>when(FunctionCommon.getSinkType(null)).thenReturn(getClass());

        SinkConfig sinkConfig = Generate.createSinkConfig(tenant, namespace, componentName);
        MeshWorkerService meshWorkerService =
                PowerMockito.mock(MeshWorkerService.class);

        Map<String, String> env = new HashMap<String, String>(){
            {
                put("unique", "unique");
                put("shared", "shared");
                put("shared2", "shared2");
            }
        };
        Map<String, String> sinkEnv = new HashMap<String, String>(){
            {
                put("shared", "shared-sink");
                put("shared2", "shared2-sink");
                put("sink", "sink");
            }
        };
        MeshWorkerServiceCustomConfig meshWorkerServiceCustomConfig =
                PowerMockito.mock(MeshWorkerServiceCustomConfig.class);
        PowerMockito.when(meshWorkerServiceCustomConfig.getEnv()).thenReturn(env);
        PowerMockito.when(meshWorkerServiceCustomConfig.getSinkEnv()).thenReturn(sinkEnv);
        PowerMockito.when(meshWorkerService.getMeshWorkerServiceCustomConfig())
                .thenReturn(meshWorkerServiceCustomConfig);

        WorkerConfig workerConfig = PowerMockito.mock(WorkerConfig.class);
        PowerMockito.when(meshWorkerService.getWorkerConfig()).thenReturn(workerConfig);

        V1alpha1Sink actualV1alpha1Sink =
                SinksUtil.createV1alpha1SkinFromSinkConfig(
                        kind, group, version, componentName, null, uploadedInputStream, sinkConfig, null,
                        null, meshWorkerService);

        Assert.assertEquals(actualV1alpha1Sink.getKind(), kind);
        V1alpha1SinkSpec v1alpha1SinkSpec = actualV1alpha1Sink.getSpec();
        Assert.assertEquals(v1alpha1SinkSpec.getClassName(), className);
        Assert.assertEquals(v1alpha1SinkSpec.getCleanupSubscription(), true);
        Assert.assertEquals(v1alpha1SinkSpec.getReplicas().intValue(), parallelism);
        Assert.assertEquals(v1alpha1SinkSpec.getInput().getTopics().get(0), inputTopic);
        Assert.assertEquals(v1alpha1SinkSpec.getPulsar().getPulsarConfig(),
                CommonUtil.getPulsarClusterConfigMapName(clusterName));
        Assert.assertEquals(v1alpha1SinkSpec.getInput().getTypeClassName(), typeClassName);
        Assert.assertEquals(v1alpha1SinkSpec.getJava().getJar(), jar);
        Assert.assertEquals(v1alpha1SinkSpec.getAutoAck(), autoAck);
        Assert.assertEquals(v1alpha1SinkSpec.getSinkConfig(), configs);
        Assert.assertEquals(v1alpha1SinkSpec.getPod().getEnv().stream().collect(Collectors.toMap(
                V1alpha1SinkSpecPodEnv::getName, V1alpha1SinkSpecPodEnv::getValue)), new HashMap<String, String>(){
            {
                put("unique", "unique");
                put("shared", "shared-sink");
                put("sink", "sink");
                put("runtime", "runtime-env");
                put("shared2", "shared2-runtime");
            }
        } );
        Assert.assertEquals(v1alpha1SinkSpec.getSubscriptionName(), "test-sub");
        Assert.assertEquals(v1alpha1SinkSpec.getSubscriptionPosition(), V1alpha1SinkSpec.SubscriptionPositionEnum.EARLIEST);
    }

    @Test
    public void testCreateSinkConfigFromV1alpha1Sink() throws ClassNotFoundException, IOException {
        String tenant = "public";
        String namespace = "default";
        String componentName = "sink-es-sample";
        String className = "org.apache.pulsar.io.elasticsearch.ElasticSearchSink";

        File narFile = PowerMockito.mock(File.class);
        PowerMockito.when(narFile.getPath()).thenReturn("");
        FileInputStream uploadedInputStream = PowerMockito.mock(FileInputStream.class);

        NarClassLoader narClassLoader = PowerMockito.mock(NarClassLoader.class);
        PowerMockito.when(narClassLoader.loadClass(className)).thenReturn(null);
        PowerMockito.mockStatic(FunctionCommon.class);
        PowerMockito.mockStatic(ConnectorUtils.class);
        PowerMockito.mockStatic(FileUtils.class);
        PowerMockito.when(FunctionCommon.extractNarClassLoader(narFile, null))
                .thenReturn(narClassLoader);
        PowerMockito.when(FunctionCommon.createPkgTempFile()).thenReturn(narFile);
        PowerMockito.when(ConnectorUtils.getIOSourceClass(narClassLoader)).thenReturn(className);
        PowerMockito.<Class<?>>when(FunctionCommon.getSourceType(null)).thenReturn(getClass());

        SinkConfig sinkConfig = Generate.createSinkConfig(tenant, namespace, componentName);
        // test whether will it filter out env got from the custom config
        String expectedCustomRuntimeOptions = sinkConfig.getCustomRuntimeOptions();
        CustomRuntimeOptions customRuntimeOptions =
                CommonUtil.getCustomRuntimeOptions(sinkConfig.getCustomRuntimeOptions());
        customRuntimeOptions.getEnv().put("unique", "unique");
        customRuntimeOptions.getEnv().put("shared", "shared-sink");
        customRuntimeOptions.getEnv().put("sink", "sink");
        String customRuntimeOptionsJSON = new Gson().toJson(customRuntimeOptions, CustomRuntimeOptions.class);
        sinkConfig.setCustomRuntimeOptions(customRuntimeOptionsJSON);

        MeshWorkerService meshWorkerService =
                PowerMockito.mock(MeshWorkerService.class);

        Map<String, String> env = new HashMap<String, String>(){
            {
                put("unique", "unique");
                put("shared", "shared");
                put("shared2", "shared2");
            }
        };
        Map<String, String> sinkEnv = new HashMap<String, String>(){
            {
                put("shared", "shared-sink");
                put("shared2", "shared2-sink");
                put("sink", "sink");
            }
        };
        MeshWorkerServiceCustomConfig meshWorkerServiceCustomConfig =
                PowerMockito.mock(MeshWorkerServiceCustomConfig.class);
        PowerMockito.when(meshWorkerServiceCustomConfig.getEnv()).thenReturn(env);
        PowerMockito.when(meshWorkerServiceCustomConfig.getSinkEnv()).thenReturn(sinkEnv);
        PowerMockito.when(meshWorkerService.getMeshWorkerServiceCustomConfig())
                .thenReturn(meshWorkerServiceCustomConfig);

        WorkerConfig workerConfig = PowerMockito.mock(WorkerConfig.class);
        PowerMockito.when(meshWorkerService.getWorkerConfig()).thenReturn(workerConfig);

        V1alpha1Sink actualV1alpha1Sink =
                SinksUtil.createV1alpha1SkinFromSinkConfig(
                        kind, group, version, componentName, null, uploadedInputStream, sinkConfig, null,
                        null, meshWorkerService);

        SinkConfig newSinkConfig =
                SinksUtil.createSinkConfigFromV1alpha1Sink(tenant, namespace, componentName, actualV1alpha1Sink, meshWorkerService);

        Assert.assertEquals(sinkConfig.getName(), newSinkConfig.getName());
        Assert.assertEquals(sinkConfig.getNamespace(), newSinkConfig.getNamespace());
        Assert.assertEquals(sinkConfig.getTenant(), newSinkConfig.getTenant());
        Assert.assertEquals(sinkConfig.getConfigs(), newSinkConfig.getConfigs());
        Assert.assertEquals(sinkConfig.getArchive(), newSinkConfig.getArchive());
        Assert.assertEquals(sinkConfig.getResources(), newSinkConfig.getResources());
        Assert.assertEquals(sinkConfig.getClassName(), newSinkConfig.getClassName());
        Assert.assertEquals(sinkConfig.getAutoAck(), newSinkConfig.getAutoAck());
        Assert.assertEquals(expectedCustomRuntimeOptions, newSinkConfig.getCustomRuntimeOptions());
        Assert.assertArrayEquals(sinkConfig.getInputs().toArray(), newSinkConfig.getInputs().toArray());
        Assert.assertEquals(sinkConfig.getMaxMessageRetries(), newSinkConfig.getMaxMessageRetries());
        Assert.assertEquals(sinkConfig.getCleanupSubscription(), newSinkConfig.getCleanupSubscription());
        Assert.assertEquals(sinkConfig.getParallelism(), newSinkConfig.getParallelism());
        Assert.assertEquals(sinkConfig.getRuntimeFlags(), newSinkConfig.getRuntimeFlags());
        Assert.assertEquals(sinkConfig.getSourceSubscriptionName(), newSinkConfig.getSourceSubscriptionName());
        Assert.assertEquals(sinkConfig.getSourceSubscriptionPosition(), newSinkConfig.getSourceSubscriptionPosition());
    }

    @Test
    public void testCreateV1alpha1SinkFromSinkConfigWithBuiltin()
            throws ClassNotFoundException, IOException {
        String tenant = "public";
        String namespace = "default";
        String componentName = "sink-es-sample";
        String className = "org.apache.pulsar.io.elasticsearch.ElasticSearchSink";
        String inputTopic = "persistent://public/default/input";
        String typeClassName = "[B";
        String archive = "connectors/pulsar-io-elastic-search-2.7.0-rc-pm-3.nar";
        boolean autoAck = true;
        int parallelism = 1;
        String clusterName = "test-pulsar";
        Map<String, Object> configs = new HashMap<>();
        configs.put("elasticSearchUrl", "https://testing-es.app");

        MeshConnectorsManager connectorsManager = PowerMockito.mock(MeshConnectorsManager.class);
        FunctionMeshConnectorDefinition connectorDefinition = PowerMockito.mock(FunctionMeshConnectorDefinition.class);
        PowerMockito.when(connectorDefinition.getId()).thenReturn("elastic-search");
        PowerMockito.when(connectorDefinition.getVersion()).thenReturn("2.7.0-rc-pm-3");
        PowerMockito.when(connectorDefinition.getImageTag()).thenReturn("2.7.0-rc-pm-3");
        PowerMockito.when(connectorDefinition.getImageRepository()).thenReturn("streamnative/pulsar-io-elastic-search");
        PowerMockito.when(connectorDefinition.getJar())
                .thenReturn("connectors/pulsar-io-elastic-search-2.7.0-rc-pm-3.nar");
        PowerMockito.when(connectorsManager.getConnectorDefinition("elastic-search")).thenReturn(connectorDefinition);

        SinkConfig sinkConfig = Generate.createSinkConfigBuiltin(tenant, namespace, componentName);
        MeshWorkerService meshWorkerService =
                PowerMockito.mock(MeshWorkerService.class);
        PowerMockito.when(meshWorkerService.getMeshWorkerServiceCustomConfig())
                .thenReturn(new MeshWorkerServiceCustomConfig());

        V1alpha1Sink actualV1alpha1Sink =
                SinksUtil.createV1alpha1SkinFromSinkConfig(
                        kind, group, version, componentName, null, null, sinkConfig, connectorsManager,
                        null, meshWorkerService);

        Assert.assertEquals(actualV1alpha1Sink.getKind(), kind);
        V1alpha1SinkSpec v1alpha1SinkSpec = actualV1alpha1Sink.getSpec();
        Assert.assertEquals(v1alpha1SinkSpec.getClassName(), className);
        Assert.assertEquals(v1alpha1SinkSpec.getCleanupSubscription(), true);
        Assert.assertEquals(v1alpha1SinkSpec.getReplicas().intValue(), parallelism);
        Assert.assertEquals(v1alpha1SinkSpec.getInput().getTopics().get(0), inputTopic);
        Assert.assertEquals(v1alpha1SinkSpec.getPulsar().getPulsarConfig(),
                CommonUtil.getPulsarClusterConfigMapName(clusterName));
        Assert.assertEquals(v1alpha1SinkSpec.getInput().getTypeClassName(), typeClassName);
        Assert.assertEquals(v1alpha1SinkSpec.getJava().getJar(), archive);
        Assert.assertEquals(v1alpha1SinkSpec.getAutoAck(), autoAck);
        Assert.assertEquals(v1alpha1SinkSpec.getSinkConfig(), configs);
    }
}
