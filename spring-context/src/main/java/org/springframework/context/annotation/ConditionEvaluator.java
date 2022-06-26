/*
 * Copyright 2002-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package org.springframework.context.annotation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ConfigurationCondition.ConfigurationPhase;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.core.env.Environment;
import org.springframework.core.env.EnvironmentCapable;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.core.type.AnnotationMetadata;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.MultiValueMap;

/**
 * Internal class used to evaluate {@link Conditional} annotations.
 *
 * @author Phillip Webb
 * @author Juergen Hoeller
 * @since 4.0
 */
class ConditionEvaluator {

	private final ConditionContextImpl context;


	/**
	 * Create a new {@link ConditionEvaluator} instance.
	 */
	public ConditionEvaluator(@Nullable BeanDefinitionRegistry registry,
			@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

		this.context = new ConditionContextImpl(registry, environment, resourceLoader);
	}


	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * The {@link ConfigurationPhase} will be deduced from the type of item (i.e. a
	 * {@code @Configuration} class will be {@link ConfigurationPhase#PARSE_CONFIGURATION})
	 * @param metadata the meta data
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(AnnotatedTypeMetadata metadata) {
		return shouldSkip(metadata, null);
	}

	/**
	 * Determine if an item should be skipped based on {@code @Conditional} annotations.
	 * @param metadata the meta data
	 * @param phase the phase of the call
	 * @return if the item should be skipped
	 */
	public boolean shouldSkip(@Nullable AnnotatedTypeMetadata metadata, @Nullable ConfigurationPhase phase) {
		// metadata 为空或者配置类中不存在 @Configuration 标签
		if (metadata == null || !metadata.isAnnotated(Conditional.class.getName())) {
			return false;
		}

		// 采用递归的方式调用，第一次执行的时候 phase 为空，向下判断
		if (phase == null) {
			// 下面的逻辑需要进入 ConfigurationClassUtils.isConfigurationCandidate() 方法，主要逻辑如下
			// 1：metaData 是 AnnotationMetadata 的一个实例
			// 2：检查 bean 中是否使用 @Configuraton 注解
			// 检查 bean 是否为一个接口
			// 检查 bean 中是否包含 @Bean,@Component、@ComponentScan、@Import、@Import、@Resource注解
			if (metadata instanceof AnnotationMetadata &&
					ConfigurationClassUtils.isConfigurationCandidate((AnnotationMetadata) metadata)) {
				return shouldSkip(metadata, ConfigurationPhase.PARSE_CONFIGURATION);
			}
			return shouldSkip(metadata, ConfigurationPhase.REGISTER_BEAN);
		}

		List<Condition> conditions = new ArrayList<>();
		for (String[] conditionClasses : getConditionClasses(metadata)) {
			for (String conditionClass : conditionClasses) {
				// 获取到 @Configuration 注解后面的 value数据
				Condition condition = getCondition(conditionClass, this.context.getClassLoader());
				conditions.add(condition);
			}
		}

		// 对相关的条件进行排序
		AnnotationAwareOrderComparator.sort(conditions);

		for (Condition condition : conditions) {
			ConfigurationPhase requiredPhase = null;
			if (condition instanceof ConfigurationCondition) {
				requiredPhase = ((ConfigurationCondition) condition).getConfigurationPhase();
			}
			// requiredPhase 只可能为空或者是 ConfigurationCondition 的实例
			if ((requiredPhase == null || requiredPhase == phase) && !condition.matches(this.context, metadata)) {
				// 此逻辑为 ：
				// 1：requiredPhase不是 ConfigurationCondition 的实例
				// 2：phase == requiredPhase，从上述递归可知，phase 可以为 ConfigurationPhase.PARSE_CONFIGURATION 或者 ConfigurationPhase.REGISTER_BEAN
				// 3：condition.match(this.context, metadata) 返回为 false
				// 如果 1，2或1，3成立，则在此函数的上层将阻断bean 注入 Spring 容器
				return true;
			}
		}

		return false;
	}

	@SuppressWarnings("unchecked")
	private List<String[]> getConditionClasses(AnnotatedTypeMetadata metadata) {
		MultiValueMap<String, Object> attributes = metadata.getAllAnnotationAttributes(Conditional.class.getName(), true);
		Object values = (attributes != null ? attributes.get("value") : null);
		return (List<String[]>) (values != null ? values : Collections.emptyList());
	}

	private Condition getCondition(String conditionClassName, @Nullable ClassLoader classloader) {
		Class<?> conditionClass = ClassUtils.resolveClassName(conditionClassName, classloader);
		return (Condition) BeanUtils.instantiateClass(conditionClass);
	}


	/**
	 * Implementation of a {@link ConditionContext}.
	 */
	private static class ConditionContextImpl implements ConditionContext {

		@Nullable
		private final BeanDefinitionRegistry registry;

		@Nullable
		private final ConfigurableListableBeanFactory beanFactory;

		private final Environment environment;

		private final ResourceLoader resourceLoader;

		@Nullable
		private final ClassLoader classLoader;

		public ConditionContextImpl(@Nullable BeanDefinitionRegistry registry,
				@Nullable Environment environment, @Nullable ResourceLoader resourceLoader) {

			this.registry = registry;
			this.beanFactory = deduceBeanFactory(registry);
			this.environment = (environment != null ? environment : deduceEnvironment(registry));
			this.resourceLoader = (resourceLoader != null ? resourceLoader : deduceResourceLoader(registry));
			this.classLoader = deduceClassLoader(resourceLoader, this.beanFactory);
		}

		@Nullable
		private ConfigurableListableBeanFactory deduceBeanFactory(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ConfigurableListableBeanFactory) {
				return (ConfigurableListableBeanFactory) source;
			}
			if (source instanceof ConfigurableApplicationContext) {
				return (((ConfigurableApplicationContext) source).getBeanFactory());
			}
			return null;
		}

		private Environment deduceEnvironment(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof EnvironmentCapable) {
				return ((EnvironmentCapable) source).getEnvironment();
			}
			return new StandardEnvironment();
		}

		private ResourceLoader deduceResourceLoader(@Nullable BeanDefinitionRegistry source) {
			if (source instanceof ResourceLoader) {
				return (ResourceLoader) source;
			}
			return new DefaultResourceLoader();
		}

		@Nullable
		private ClassLoader deduceClassLoader(@Nullable ResourceLoader resourceLoader,
				@Nullable ConfigurableListableBeanFactory beanFactory) {

			if (resourceLoader != null) {
				ClassLoader classLoader = resourceLoader.getClassLoader();
				if (classLoader != null) {
					return classLoader;
				}
			}
			if (beanFactory != null) {
				return beanFactory.getBeanClassLoader();
			}
			return ClassUtils.getDefaultClassLoader();
		}

		@Override
		public BeanDefinitionRegistry getRegistry() {
			Assert.state(this.registry != null, "No BeanDefinitionRegistry available");
			return this.registry;
		}

		@Override
		@Nullable
		public ConfigurableListableBeanFactory getBeanFactory() {
			return this.beanFactory;
		}

		@Override
		public Environment getEnvironment() {
			return this.environment;
		}

		@Override
		public ResourceLoader getResourceLoader() {
			return this.resourceLoader;
		}

		@Override
		@Nullable
		public ClassLoader getClassLoader() {
			return this.classLoader;
		}
	}

}
