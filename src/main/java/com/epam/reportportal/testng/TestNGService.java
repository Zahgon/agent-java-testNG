/*
 * Copyright 2019 EPAM Systems
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.epam.reportportal.testng;

import com.epam.reportportal.annotations.*;
import com.epam.reportportal.annotations.attribute.Attributes;
import com.epam.reportportal.listeners.ItemStatus;
import com.epam.reportportal.listeners.ListenerParameters;
import com.epam.reportportal.service.Launch;
import com.epam.reportportal.service.ReportPortal;
import com.epam.reportportal.service.item.TestCaseIdEntry;
import com.epam.reportportal.service.tree.TestItemTree;
import com.epam.reportportal.testng.util.internal.LimitedSizeConcurrentHashMap;
import com.epam.reportportal.utils.*;
import com.epam.reportportal.utils.formatting.MarkdownUtils;
import com.epam.reportportal.utils.properties.SystemAttributesExtractor;
import com.epam.ta.reportportal.ws.model.*;
import com.epam.ta.reportportal.ws.model.attribute.ItemAttributesRQ;
import com.epam.ta.reportportal.ws.model.launch.StartLaunchRQ;
import com.epam.ta.reportportal.ws.model.log.SaveLogRQ;
import io.reactivex.Maybe;
import jakarta.annotation.Nonnull;
import jakarta.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.testng.*;
import org.testng.annotations.Factory;
import org.testng.annotations.Parameters;
import org.testng.annotations.Test;
import org.testng.collections.Lists;
import org.testng.internal.ConstructorOrMethod;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlTest;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import static com.epam.reportportal.testng.util.ItemTreeUtils.createKey;
import static com.epam.reportportal.utils.formatting.ExceptionUtils.getStackTrace;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;

/**
 * TestNG service implements operations for interaction ReportPortal
 */
public class TestNGService implements ITestNGService {

    private static final Set<TestMethodType> BEFORE_METHODS = Stream.of(TestMethodType.BEFORE_TEST, TestMethodType.BEFORE_SUITE, TestMethodType.BEFORE_GROUPS, TestMethodType.BEFORE_CLASS, TestMethodType.BEFORE_METHOD).collect(Collectors.toSet());

    private static final String AGENT_PROPERTIES_FILE = "agent.properties";

    private static final Set<String> TESTNG_INVOKERS = Stream.of("org.testng.internal.TestInvoker", "org.testng.internal.invokers.TestInvoker").collect(Collectors.toSet());

    private static final Predicate<StackTraceElement> IS_RETRY_ELEMENT = e -> TESTNG_INVOKERS.contains(e.getClassName()) && "retryFailed".equals(e.getMethodName());

    private static final Predicate<StackTraceElement[]> IS_RETRY = eList -> Arrays.stream(eList).anyMatch(IS_RETRY_ELEMENT);

    private static final int MAXIMUM_HISTORY_SIZE = 1000;

    public static final String SKIPPED_ISSUE_KEY = "skippedIssue";

    public static final String RP_ID = "rp_id";

    public static final String RP_RETRY = "rp_retry";

    public static final String RP_METHOD_TYPE = "rp_method_type";

    public static final String NULL_VALUE = "NULL";

    public static final String DESCRIPTION_ERROR_FORMAT = "Error: \n%s";

    public static final TestItemTree ITEM_TREE = new TestItemTree();

    private final Map<Object, Queue<Pair<Maybe<String>, FinishTestItemRQ>>> BEFORE_METHOD_TRACKER = new ConcurrentHashMap<>();

    private final Map<Object, Boolean> RETRY_STATUS_TRACKER = new LimitedSizeConcurrentHashMap<>(MAXIMUM_HISTORY_SIZE);

    private final Map<Object, Boolean> SKIPPED_STATUS_TRACKER = new LimitedSizeConcurrentHashMap<>(MAXIMUM_HISTORY_SIZE);

    private final MemoizingSupplier<Launch> launch;

    private volatile Thread shutDownHook;

    private static Thread getShutdownHook(final Supplier<Launch> launch) {
        return new Thread(() -> {
            FinishExecutionRQ rq = new FinishExecutionRQ();
            rq.setEndTime(Instant.now());
            launch.get().finish(rq);
        });
    }

    public TestNGService(@Nonnull final ReportPortal reportPortal) {
        this.launch = new MemoizingSupplier<>(() -> {
            //this reads property, so we want to
            //init ReportPortal object each time Launch object is going to be created
            StartLaunchRQ startRq = buildStartLaunchRq(reportPortal.getParameters());
            startRq.setStartTime(Instant.now());
            Launch newLaunch = reportPortal.newLaunch(startRq);
            shutDownHook = getShutdownHook(() -> newLaunch);
            Runtime.getRuntime().addShutdownHook(shutDownHook);
            return newLaunch;
        });
    }

    public TestNGService(Supplier<Launch> launchSupplier) {
        launch = new MemoizingSupplier<>(launchSupplier);
        shutDownHook = getShutdownHook(launch);
        Runtime.getRuntime().addShutdownHook(shutDownHook);
    }

    @Override
    public void startLaunch() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void finishLaunch() {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void addToTree(ISuite suite, Maybe<String> item) {
        ITEM_TREE.getTestItems().put(createKey(suite), TestItemTree.createTestItemLeaf(item));
    }

    @Override
    public void startTestSuite(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @SuppressWarnings("unchecked")
    protected <T> T getAttribute(IAttributes attributes, String attribute) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void finishTestSuite(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void removeFromTree(ISuite suite) {
        ITEM_TREE.getTestItems().remove(createKey(suite));
    }

    @Override
    public void startTest(ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void addToTree(ITestContext testContext, Maybe<String> testId) {
        ofNullable(ITEM_TREE.getTestItems().get(createKey(testContext.getSuite()))).ifPresent(suiteLeaf -> {
            List<XmlClass> testClasses = testContext.getCurrentXmlTest().getClasses();
            ConcurrentHashMap<TestItemTree.ItemTreeKey, TestItemTree.TestItemLeaf> testClassesMapping = new ConcurrentHashMap<>(testClasses.size());
            for (XmlClass testClass : testClasses) {
                TestItemTree.TestItemLeaf testClassLeaf = TestItemTree.createTestItemLeaf(testId, new ConcurrentHashMap<>());
                testClassesMapping.put(createKey(testClass), testClassLeaf);
            }
            suiteLeaf.getChildItems().put(createKey(testContext), TestItemTree.createTestItemLeaf(testId, testClassesMapping));
        });
    }

    private static Set<ITestResult> getTestResults(IResultMap rm) {
        return ofNullable(rm).map(IResultMap::getAllResults).orElse(Collections.emptySet());
    }

    @Override
    public void finishTest(ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void removeFromTree(ITestContext testContext) {
        ofNullable(ITEM_TREE.getTestItems().get(createKey(testContext.getSuite()))).ifPresent(suiteLeaf -> suiteLeaf.getChildItems().remove(createKey(testContext)));
    }

    private boolean isRetry(ITestResult testResult) {
        if (testResult.wasRetried()) {
            return true;
        }
        Object instance = testResult.getInstance();
        if (instance != null && RETRY_STATUS_TRACKER.containsKey(instance)) {
            return true;
        }
        return IS_RETRY.test(Thread.currentThread().getStackTrace());
    }

    /**
     * Extension point to customize beforeXXX creation event/request
     *
     * @param testResult TestNG's testResult context
     * @param type       Type of method
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartConfigurationRq(@Nonnull ITestResult testResult, @Nullable TestMethodType type) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void startConfiguration(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step description
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Description being sent to ReportPortal
     */
    @Nonnull
    protected String createStepDescription(@Nonnull ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step creation event/request
     *
     * @param testResult TestNG's testResult context
     * @param type       method type
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartStepRq(@Nonnull final ITestResult testResult, @Nonnull final TestMethodType type) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step creation event/request
     *
     * @param testResult TestNG's testResult context
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartStepRq(@Nonnull final ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void addToTree(ITestResult testResult, Maybe<String> stepMaybe) {
        ITestContext testContext = testResult.getTestContext();
        ofNullable(ITEM_TREE.getTestItems().get(createKey(testContext.getSuite()))).flatMap(suiteLeaf -> ofNullable(suiteLeaf.getChildItems().get(createKey(testContext))).flatMap(testLeaf -> ofNullable(testLeaf.getChildItems().get(createKey(testResult.getTestClass()))))).ifPresent(testClassLeaf -> testClassLeaf.getChildItems().put(createKey(testResult), TestItemTree.createTestItemLeaf(stepMaybe)));
    }

    @Override
    public void startTestMethod(@Nonnull ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step description with error message
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Description being sent to ReportPortal
     */
    @Nullable
    private String getLogMessage(@Nonnull ITestResult testResult) {
        String error = ofNullable(testResult.getThrowable()).map(t -> String.format(DESCRIPTION_ERROR_FORMAT, getStackTrace(t, new Throwable()))).orElse(null);
        if (error == null) {
            return null;
        }
        String description = createStepDescription(testResult);
        return StringUtils.isNotBlank(description) ? MarkdownUtils.asTwoParts(description, error) : error;
    }

    /**
     * Extension point to customize test method on it's finish
     *
     * @param status     item execution status
     * @param testResult TestNG's testResult context
     * @return Request to ReportPortal
     */
    @Nonnull
    protected FinishTestItemRQ buildFinishTestMethodRq(@Nonnull ItemStatus status, @Nonnull ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    private void updateTestItemTree(Maybe<OperationCompletionRS> finishItemResponse, ITestResult testResult) {
        ITestContext testContext = testResult.getTestContext();
        TestItemTree.TestItemLeaf suiteLeaf = ITEM_TREE.getTestItems().get(createKey(testContext.getSuite()));
        if (suiteLeaf != null) {
            TestItemTree.TestItemLeaf testLeaf = suiteLeaf.getChildItems().get(createKey(testContext));
            if (testLeaf != null) {
                TestItemTree.TestItemLeaf testClassLeaf = testLeaf.getChildItems().get(createKey(testResult.getTestClass()));
                if (testClassLeaf != null) {
                    TestItemTree.TestItemLeaf testItemLeaf = testClassLeaf.getChildItems().get(createKey(testResult));
                    if (testItemLeaf != null) {
                        testItemLeaf.setFinishResponse(finishItemResponse);
                    }
                }
            }
        }
    }

    private void processFinishRetryFlag(ITestResult testResult, FinishTestItemRQ rq) {
        Object instance = testResult.getInstance();
        if (instance != null && !ItemStatus.SKIPPED.name().equals(rq.getStatus())) {
            // Remove retry flag if an item passed
            RETRY_STATUS_TRACKER.remove(instance);
        }
        TestMethodType type = getAttribute(testResult, RP_METHOD_TYPE);
        boolean isRetried = testResult.wasRetried();
        if (TestMethodType.STEP == type && getAttribute(testResult, RP_RETRY) == null && isRetried && rq.getIssue() == null) {
            RETRY_STATUS_TRACKER.put(instance, Boolean.TRUE);
            rq.setRetry(Boolean.TRUE);
            rq.setIssue(Launch.NOT_ISSUE);
        }
        if (isRetried) {
            testResult.setAttribute(RP_RETRY, Boolean.TRUE);
        }
        // Save before method finish requests to update them with a retry flag in case of main test method failed
        if (instance != null) {
            if (TestMethodType.BEFORE_METHOD == type && getAttribute(testResult, RP_RETRY) == null) {
                Maybe<String> itemId = getAttribute(testResult, RP_ID);
                BEFORE_METHOD_TRACKER.computeIfAbsent(instance, i -> new ConcurrentLinkedQueue<>()).add(Pair.of(itemId, rq));
            } else {
                Queue<Pair<Maybe<String>, FinishTestItemRQ>> beforeFinish = BEFORE_METHOD_TRACKER.remove(instance);
                if (beforeFinish != null && isRetried) {
                    beforeFinish.stream().filter(e -> e.getValue().isRetry() == null || !e.getValue().isRetry()).forEach(e -> {
                        FinishTestItemRQ f = e.getValue();
                        f.setRetry(true);
                        //noinspection ReactiveStreamsUnusedPublisher
                        launch.get().finishTestItem(e.getKey(), f);
                    });
                }
            }
        }
    }

    /**
     * Extension point to customize skipped test insides
     *
     * @param testResult TestNG's testResult context
     */
    @SuppressWarnings("unused")
    protected void createSkippedSteps(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Nullable
    protected com.epam.ta.reportportal.ws.model.issue.Issue createIssue(@Nonnull ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void finishTestMethod(ItemStatus status, ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Override
    public void sendReportPortalMsg(final ITestResult result) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize suite creation event/request
     *
     * @param suite TestNG suite
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartSuiteRq(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test creation event/request
     *
     * @param testContext TestNG test context
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartTestItemRQ buildStartTestItemRq(@Nonnull ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize launch creation event/request
     *
     * @param parameters Launch Configuration parameters
     * @return Request to ReportPortal
     */
    @Nonnull
    protected StartLaunchRQ buildStartLaunchRq(ListenerParameters parameters) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize launch finishing event/request
     *
     * @param parameters Launch Configuration parameters
     * @return Request to ReportPortal
     */
    @SuppressWarnings("unused")
    @Nonnull
    protected FinishExecutionRQ buildFinishLaunchRq(ListenerParameters parameters) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test suite on it's finish
     *
     * @param suite TestNG's suite context
     * @return Request to ReportPortal
     */
    @SuppressWarnings("unused")
    @Nonnull
    protected FinishTestItemRQ buildFinishTestSuiteRq(ISuite suite) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test on it's finish
     *
     * @param testContext TestNG test context
     * @return Request to ReportPortal
     */
    @Nonnull
    protected FinishTestItemRQ buildFinishTestRq(ITestContext testContext) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize ReportPortal test parameters
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Parameters being sent to ReportPortal
     */
    @Nullable
    protected List<ParameterResource> createStepParameters(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Process testResult to create parameters provided via {@link Parameters}
     *
     * @param testResult TestNG's testResult context
     * @return Step Parameters being sent to ReportPortal
     */
    @Nonnull
    private List<ParameterResource> createAnnotationParameters(@Nonnull ITestResult testResult) {
        return getMethodAnnotation(Parameters.class, testResult).map(a -> {
            String[] keys = a.value();
            Object[] parameters = testResult.getParameters();
            if (parameters.length != keys.length || keys.length <= 0) {
                return Collections.<ParameterResource>emptyList();
            }
            return IntStream.range(0, keys.length).mapToObj(i -> {
                ParameterResource parameter = new ParameterResource();
                parameter.setKey(keys[i]);
                parameter.setValue(parameters[i] == null ? NULL_VALUE : parameters[i].toString());
                return parameter;
            }).collect(toList());
        }).orElse(Collections.emptyList());
    }

    /**
     * Processes testResult to create parameters provided
     * by {@link org.testng.annotations.DataProvider} If parameter key isn't provided
     * by {@link ParameterKey} annotation then it will be 'arg[index]'
     *
     * @param testResult TestNG's testResult context
     * @return Step Parameters being sent to ReportPortal
     */
    @Nonnull
    private List<ParameterResource> createDataProviderParameters(@Nonnull ITestResult testResult) {
        return getMethodAnnotation(Test.class, testResult).map(a -> {
            Method method = getMethod(testResult);
            Object[] parameters = testResult.getParameters();
            if (method == null || isBlank(a.dataProvider()) || parameters == null || parameters.length <= 0) {
                return Collections.<ParameterResource>emptyList();
            }
            return ParameterUtils.getParameters(method, Arrays.asList(parameters));
        }).orElse(Collections.emptyList());
    }

    private List<ParameterResource> crateFactoryParameters(ITestResult testResult) {
        Object[] parameters = testResult.getFactoryParameters();
        Constructor<?>[] constructors = ofNullable(getMethod(testResult)).map(Method::getDeclaringClass).map(Class::getConstructors).orElse(new Constructor<?>[0]);
        Constructor<?> constructor = Arrays.stream(constructors).filter(c -> {
            Factory factoryAnnotation = c.getAnnotation(Factory.class);
            if (factoryAnnotation == null) {
                return false;
            }
            if (c.getParameterCount() != parameters.length) {
                return false;
            }
            Class<?>[] types = c.getParameterTypes();
            return IntStream.range(0, types.length).mapToObj(i -> {
                Class<?> type = types[i];
                Class<?> boxedClass = ParameterUtils.toBoxedType(type);
                // If value is null we can't get class, assume it suites.
                return ofNullable(parameters[i]).map(p -> boxedClass == p.getClass()).orElse(true);
            }).allMatch(b -> b);
        }).findAny().orElse(null);
        if (parameters == null || parameters.length <= 0 || constructor == null) {
            return Collections.emptyList();
        }
        return ParameterUtils.getParameters(constructor, Arrays.asList(parameters));
    }

    /**
     * Extension point to customize beforeXXX step name
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Name being sent to ReportPortal
     */
    protected String createConfigurationName(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize beforeXXX step description
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Description being sent to ReportPortal
     */
    protected String createConfigurationDescription(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    /**
     * Extension point to customize test step name
     *
     * @param testResult TestNG's testResult context
     * @return Test/Step Name being sent to ReportPortal
     */
    protected String createStepName(ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Nullable
    private TestCaseIdEntry getTestCaseId(@Nonnull String codeRef, @Nonnull ITestResult testResult) {
        Method method = getMethod(testResult);
        Object instance = testResult.getInstance();
        List<Object> parameters = ofNullable(testResult.getParameters()).map(Arrays::asList).orElse(null);
        TestCaseIdEntry id = getMethodAnnotation(TestCaseId.class, testResult).flatMap(a -> ofNullable(method).map(m -> TestCaseIdUtils.getTestCaseId(a, m, codeRef, parameters, instance))).orElse(TestCaseIdUtils.getTestCaseId(codeRef, parameters));
        return id == null ? null : id.getId().endsWith("[]") ? new TestCaseIdEntry(id.getId().substring(0, id.getId().length() - 2)) : id;
    }

    @Nullable
    protected Set<ItemAttributesRQ> createStepAttributes(@Nonnull ITestResult testResult) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }

    @Nullable
    private Method getMethod(@Nonnull ITestResult testResult) {
        return ofNullable(testResult.getMethod()).map(ITestNGMethod::getConstructorOrMethod).map(ConstructorOrMethod::getMethod).orElse(null);
    }

    /**
     * Returns method annotation by specified annotation class from
     * TestNG Method or null if the method does not contain
     * such annotation.
     *
     * @param annotation Annotation class to find
     * @param testResult Where to find
     * @return {@link Annotation} or null if doesn't exists
     */
    @Nonnull
    private <T extends Annotation> Optional<T> getMethodAnnotation(@Nonnull Class<T> annotation, @Nonnull ITestResult testResult) {
        return ofNullable(getMethod(testResult)).map(m -> m.getAnnotation(annotation));
    }

    /**
     * Checks if test suite has any methods to run.
     * It can be useful with writing test with "groups".
     * So there could be created a test suite that has some methods but doesn't fit
     * the condition of a group. Such suite should be ignored for rp.
     *
     * @param testContext Test context
     * @return True if item has any tests to run
     */
    private boolean hasMethodsToRun(ITestContext testContext) {
        return null != testContext && null != testContext.getAllTestMethods() && 0 != testContext.getAllTestMethods().length;
    }

    /**
     * Calculate parent id for configuration
     */
    Maybe<String> getConfigParent(ITestResult testResult, TestMethodType type) {
        throw new UnsupportedOperationException("STUB: not implemented");
    }
}
