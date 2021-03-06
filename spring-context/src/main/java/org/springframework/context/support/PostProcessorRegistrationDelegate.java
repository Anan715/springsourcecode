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

package org.springframework.context.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.MergedBeanDefinitionPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.OrderComparator;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;

/**
 * Delegate for AbstractApplicationContext's post-processor handling.
 *
 * @author Juergen Hoeller
 * @since 4.0
 */
final class PostProcessorRegistrationDelegate {

	private PostProcessorRegistrationDelegate() {
	}


	public static void invokeBeanFactoryPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanFactoryPostProcessor> beanFactoryPostProcessors) {

		// Invoke BeanDefinitionRegistryPostProcessors first, if any.
		// 无论什么情况，优先执行 BeanDefinitionRegistryPostProcessors，将已经执行过的 BFPP 存储在 processorBeans 中
		Set<String> processedBeans = new HashSet<>();

		// 判断 beanfactory 是否是 BeanDefinitionRegistry 类型，
		if (beanFactory instanceof BeanDefinitionRegistry) {
			// 类转换
			BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
			// 下面两个接口是不同的：BeanDefinitionRegistryPostProcessor 是 BeanFactoryPostProcessor 的子集
			// BeanFactoryPostProcessor 主要针对的操作对象是 BeanFactory,而 BeanDefinitionRegistryPostProcessor 主要针对的操作对象是 BeanDefination
			List<BeanFactoryPostProcessor> regularPostProcessors = new ArrayList<>();
			List<BeanDefinitionRegistryPostProcessor> registryProcessors = new ArrayList<>();

			// 首先处理入参中的 BeanFactoryPostProcessor，遍历所有的BeanFactoryPostProcessor，
			// 将 BeanFactoryPostProcessor 和 BeanDefinitionRegistryPostProcessor区分开
			for (BeanFactoryPostProcessor postProcessor : beanFactoryPostProcessors) {
				if (postProcessor instanceof BeanDefinitionRegistryPostProcessor) {
					BeanDefinitionRegistryPostProcessor registryProcessor =
							(BeanDefinitionRegistryPostProcessor) postProcessor;
					registryProcessor.postProcessBeanDefinitionRegistry(registry);
					// 添加到 registryProcessors，用于处理之后的 postProcessBeanFactory 方法
					registryProcessors.add(registryProcessor);
				}
				else {
					// 若是普通的 BeanfactoryPostProcessor 添加到 regularPostProcessors，用于处理之后的 postProcessBeanFactory 方法
					regularPostProcessors.add(postProcessor);
				}
			}

			// Do not initialize FactoryBeans here: We need to leave all regular beans
			// uninitialized to let the bean factory post-processors apply to them!
			// Separate between BeanDefinitionRegistryPostProcessors that implement
			// PriorityOrdered, Ordered, and the rest.
			// 用于保存本次要执行的 BeanDefinitionRegistryPostProcessor
			List<BeanDefinitionRegistryPostProcessor> currentRegistryProcessors = new ArrayList<>();

			// First, invoke the BeanDefinitionRegistryPostProcessors that implement PriorityOrdered.
			// 调用所有实现 PriorityOrdered 接口的 BeanDefinitionRegistryPostProcessor 实现类
			// 找到所有实现 BeanDefinitionRegistryPostProcessor 接口 bean 的 beanName
			String[] postProcessorNames =
					beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			// 遍历所有符合规则的 postProcessorNames
			for (String ppName : postProcessorNames) {
				// 是否实现了 PriorityOrdered 接口
				if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
					// 获取名字对应的 bean 实例，添加到 currentRegistryProcessors
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将要被执行 BFPP 的 beanName 添加到 processedBeans，避免重复执行·
					processedBeans.add(ppName);
				}
			}
			// 按照优先级排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到 registryProcessors，用于执行最后的 postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历所有的 currentRegistryProcessors，执行 postProcessBeanDefinitionRegistry 方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Next, invoke the BeanDefinitionRegistryPostProcessors that implement Ordered.
			// 调用所有实现 ordered 接口的 BeanDefinitionRegistryPostProcessor 实现类
			// 找到所有实现 BeanDefinitionRegistryPostProcessor 接口 bean 的beanName,此处需要重复查找的原因是上面的执行过程可能会增加其他的 BeanDefinitionRegistryPostProcessor
			postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
			for (String ppName : postProcessorNames) {
				// 检测是否实现了 Ordered 接口，且并未执行过
				if (!processedBeans.contains(ppName) && beanFactory.isTypeMatch(ppName, Ordered.class)) {
					// 获取名字对应的 bean实例，添加到 currentRegistryProcessors
					currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
					// 将要被执行的 BFPP 名称添加到 processedBeans，避免重复执行
					processedBeans.add(ppName);
				}
			}
			// 按照优先级排序操作
			sortPostProcessors(currentRegistryProcessors, beanFactory);
			// 添加到 registryProcessors，用于执行最后的 postProcessBeanFactory方法
			registryProcessors.addAll(currentRegistryProcessors);
			// 遍历所有的 currentRegistryProcessors，执行 postProcessBeanDefinitionRegistry 方法
			invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
			currentRegistryProcessors.clear();

			// Finally, invoke all other BeanDefinitionRegistryPostProcessors until no further ones appear.
			// 调用剩下的所有的 BeanDefinitionRegistryPostProcessor
			boolean reiterate = true;
			while (reiterate) {
				reiterate = false;
				// 找出所有实现 BeanDefinitionRegistryPostProcessor 的类
				postProcessorNames = beanFactory.getBeanNamesForType(BeanDefinitionRegistryPostProcessor.class, true, false);
				for (String ppName : postProcessorNames) {
					// 跳过已经执行过的 BeanDefinitionRegistryPostProcessor
					if (!processedBeans.contains(ppName)) {
						// 获取名字对应的 bean,添加到 currentRegistryProcessors
						currentRegistryProcessors.add(beanFactory.getBean(ppName, BeanDefinitionRegistryPostProcessor.class));
						// 将要被执行的 BFPP 名称添加到 processedBeans，避免重复执行
						processedBeans.add(ppName);
						reiterate = true;
					}
				}
				// 按照优先级排序操作
				sortPostProcessors(currentRegistryProcessors, beanFactory);
				// 添加到 registryProcessors，用于执行最后的 postProcessBeanFactory方法
				registryProcessors.addAll(currentRegistryProcessors);
				// 遍历 currentRegistryProcessors，执行 postProcessBeanDefinitionRegistry 方法
				invokeBeanDefinitionRegistryPostProcessors(currentRegistryProcessors, registry);
				currentRegistryProcessors.clear();
			}

			// Now, invoke the postProcessBeanFactory callback of all processors handled so far.
			// 调用所有的 BeanDefinitionRegistryPostProcessor 的 postProcessBeanFactory 方法
			invokeBeanFactoryPostProcessors(registryProcessors, beanFactory);
			// 调用入参 beanFactoryPostProcessors 中普通的 beanFactoryPostProcessors 的 postProcessBeanFactory 方法
			invokeBeanFactoryPostProcessors(regularPostProcessors, beanFactory);
		}

		else {
			// Invoke factory processors registered with the context instance.
			// 如果 beanFactory 不属于 BeanDefinationRegistry类型，直接执行 postProcessBeanFactory 方法
			invokeBeanFactoryPostProcessors(beanFactoryPostProcessors, beanFactory);
		}

		// 到此为止，入参 BeanFactoryPostProcessor 和容器中所有的 BeanDefinitionRegistryPostProcessor 已经全部执行完毕，
		// 下面开始处理容器中所有的 BeanFactoryPostProcessors

		// Do not initialize FactoryBeans here: We need to leave all regular beans
		// uninitialized to let the bean factory post-processors apply to them!
		// 找出所有实现 BeanFactoryPostProcessor 的类
		String[] postProcessorNames =
				beanFactory.getBeanNamesForType(BeanFactoryPostProcessor.class, true, false);

		// Separate between BeanFactoryPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 存放实现了 priorityOrdered 接口的 BeanFactoryPostProcessor
		List<BeanFactoryPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 存放实现了 ordered 接口的 BeanFactoryPostProcessor 的 beanNames
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 存放实现了 普通的 BeanFactoryPostProcessor 的 beanNames
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历 postProcessorNames，将 BeanFactoryPostProcessors 根据 PriorityOrdered、Ordered、普通区分开
		for (String ppName : postProcessorNames) {
			// 跳过已经执行的 BeanFactoryPostProcessors
			if (processedBeans.contains(ppName)) {
				// skip - already processed in first phase above
			}
			// 添加实现了 PriorityOrdered 接口的 BeanfactoryPostProcessor 到 priorityOrderedPostProcessors
			else if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				priorityOrderedPostProcessors.add(beanFactory.getBean(ppName, BeanFactoryPostProcessor.class));
			}
			// 添加实现了 Ordered 接口的 BeanfactoryPostProcessor 到 orderedPostProcessorNames
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				// 添加剩下的普通的 BeanfactoryPostProcessor 的 beanname 到 nonOrderedPostProcessorNames
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, invoke the BeanFactoryPostProcessors that implement PriorityOrdered.
		// 对实现了 PriorityOrdered 接口的 BeanfactoryPostProcessor 排序
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 遍历实现 PriorityOrdered 接口的 BeanfactoryPostProcessor ，执行 postProcessBeanFactory 方法
		invokeBeanFactoryPostProcessors(priorityOrderedPostProcessors, beanFactory);

		// Next, invoke the BeanFactoryPostProcessors that implement Ordered.
		// 创建存放实现了 Ordered 接口的 BeanFactoryPostProcessors 集合
		List<BeanFactoryPostProcessor> orderedPostProcessors = new ArrayList<>();
		// 遍历存放实现了 ordered 接口 BeanFactoryPostProcessors 名字的集合
		for (String postProcessorName : orderedPostProcessorNames) {
			// 将实现了 Ordered 接口的 BeanFactoryPostProcessors 添加到集合中
			orderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 对实现了 ordered 接口的 BeanFactoryPostProcessors 排序
		sortPostProcessors(orderedPostProcessors, beanFactory);
		// 对实现了 Ordered 接口的 BeanFactoryPostProcessors 集合执行 postProcessBeanFactory 方法
		invokeBeanFactoryPostProcessors(orderedPostProcessors, beanFactory);

		// Finally, invoke all other BeanFactoryPostProcessors.
		// 存放普通的 BeanFactoryPostProcessors
		List<BeanFactoryPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String postProcessorName : nonOrderedPostProcessorNames) {
			// 普通 BeanFactoryPostProcessors 添加到集合
			nonOrderedPostProcessors.add(beanFactory.getBean(postProcessorName, BeanFactoryPostProcessor.class));
		}
		// 普通 BeanFactoryPostProcessors 执行 postProcessBeanFactory 方法
		invokeBeanFactoryPostProcessors(nonOrderedPostProcessors, beanFactory);

		// Clear cached merged bean definitions since the post-processors might have
		// modified the original metadata, e.g. replacing placeholders in values...
		beanFactory.clearMetadataCache();
	}

	public static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, AbstractApplicationContext applicationContext) {

		// 找到实现了 BeanPostProcessor 接口的类
		String[] postProcessorNames = beanFactory.getBeanNamesForType(BeanPostProcessor.class, true, false);

		// Register BeanPostProcessorChecker that logs an info message when
		// a bean is created during BeanPostProcessor instantiation, i.e. when
		// a bean is not eligible for getting processed by all BeanPostProcessors.
		// 记录下 BeanPostProcessor 的数量，此处加一是因为此方法会加入一个 BeanPostProcessorChecker 的类
		int beanProcessorTargetCount = beanFactory.getBeanPostProcessorCount() + 1 + postProcessorNames.length;
		// 添加 BeanPostProcessorChecker (主要用于记录信息) 到 beanFactory 中
		beanFactory.addBeanPostProcessor(new BeanPostProcessorChecker(beanFactory, beanProcessorTargetCount));

		// Separate between BeanPostProcessors that implement PriorityOrdered,
		// Ordered, and the rest.
		// 存放实现了 priorityOrdered 接口的 BeanPostProcessor
		List<BeanPostProcessor> priorityOrderedPostProcessors = new ArrayList<>();
		// 存放 Spring 内部的 BeanPostProcessor
		List<BeanPostProcessor> internalPostProcessors = new ArrayList<>();
		List<String> orderedPostProcessorNames = new ArrayList<>();
		// 存放普通的 BeanPostProcessor 的name 集合
		List<String> nonOrderedPostProcessorNames = new ArrayList<>();
		// 遍历 beanfactory 中 BeanPostProcessor 的集合的 postProcessorNames
		for (String ppName : postProcessorNames) {
			if (beanFactory.isTypeMatch(ppName, PriorityOrdered.class)) {
				BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
				priorityOrderedPostProcessors.add(pp);
				if (pp instanceof MergedBeanDefinitionPostProcessor) {
					internalPostProcessors.add(pp);
				}
			}
			else if (beanFactory.isTypeMatch(ppName, Ordered.class)) {
				orderedPostProcessorNames.add(ppName);
			}
			else {
				nonOrderedPostProcessorNames.add(ppName);
			}
		}

		// First, register the BeanPostProcessors that implement PriorityOrdered.
		sortPostProcessors(priorityOrderedPostProcessors, beanFactory);
		// 注册实现了 priorityOrdered Processor 实例添加到 beanFactory 中
		registerBeanPostProcessors(beanFactory, priorityOrderedPostProcessors);

		// Next, register the BeanPostProcessors that implement Ordered.
		List<BeanPostProcessor> orderedPostProcessors = new ArrayList<>();
		for (String ppName : orderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			orderedPostProcessors.add(pp);
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		sortPostProcessors(orderedPostProcessors, beanFactory);
		registerBeanPostProcessors(beanFactory, orderedPostProcessors);

		// Now, register all regular BeanPostProcessors.
		List<BeanPostProcessor> nonOrderedPostProcessors = new ArrayList<>();
		for (String ppName : nonOrderedPostProcessorNames) {
			BeanPostProcessor pp = beanFactory.getBean(ppName, BeanPostProcessor.class);
			nonOrderedPostProcessors.add(pp);
			// 如果 ppName 对应的 BeanPostProcessor 实例也实现了 MergedBeanDefinitionPostProcessor 接口，那么将 ppName
			// 对应 bean 的实例添加到 internalPostProcessors
			if (pp instanceof MergedBeanDefinitionPostProcessor) {
				internalPostProcessors.add(pp);
			}
		}
		// 注册没有实现  priorityOrdered 和 ordered 接口的 BeanPostProcessor实例添加到 beanFactory
		registerBeanPostProcessors(beanFactory, nonOrderedPostProcessors);

		// Finally, re-register all internal BeanPostProcessors.
		// 将所有实现了 MergedBeanDefinitionPostProcessor 类型的 BeanPostProcessor 排序
		sortPostProcessors(internalPostProcessors, beanFactory);
		// 将所有实现了 MergedBeanDefinitionPostProcessor 类型的 BeanPostProcessor 注册到 BeanFactory 中
		registerBeanPostProcessors(beanFactory, internalPostProcessors);

		// Re-register post-processor for detecting inner beans as ApplicationListeners,
		// moving it to the end of the processor chain (for picking up proxies etc).
		// 注册 ApplicationListenerDetector 到 BeanFactory 中
		beanFactory.addBeanPostProcessor(new ApplicationListenerDetector(applicationContext));
	}

	private static void sortPostProcessors(List<?> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		Comparator<Object> comparatorToUse = null;
		// 判断是否是 DefaultListableBeanFactory 类型
		if (beanFactory instanceof DefaultListableBeanFactory) {
			// 获取设置的比较器
			comparatorToUse = ((DefaultListableBeanFactory) beanFactory).getDependencyComparator();
		}
		if (comparatorToUse == null) {
			// 如果没有设置比较器，则使用默认的 OrderComparator
			comparatorToUse = OrderComparator.INSTANCE;
		}
		// 使用比较器对 postProcessors 排序
		postProcessors.sort(comparatorToUse);
	}

	/**
	 * Invoke the given BeanDefinitionRegistryPostProcessor beans.
	 */
	private static void invokeBeanDefinitionRegistryPostProcessors(
			Collection<? extends BeanDefinitionRegistryPostProcessor> postProcessors, BeanDefinitionRegistry registry) {

		for (BeanDefinitionRegistryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanDefinitionRegistry(registry);
		}
	}

	/**
	 * Invoke the given BeanFactoryPostProcessor beans.
	 */
	private static void invokeBeanFactoryPostProcessors(
			Collection<? extends BeanFactoryPostProcessor> postProcessors, ConfigurableListableBeanFactory beanFactory) {

		for (BeanFactoryPostProcessor postProcessor : postProcessors) {
			postProcessor.postProcessBeanFactory(beanFactory);
		}
	}

	/**
	 * Register the given BeanPostProcessor beans.
	 */
	private static void registerBeanPostProcessors(
			ConfigurableListableBeanFactory beanFactory, List<BeanPostProcessor> postProcessors) {

		for (BeanPostProcessor postProcessor : postProcessors) {
			beanFactory.addBeanPostProcessor(postProcessor);
		}
	}


	/**
	 * BeanPostProcessor that logs an info message when a bean is created during
	 * BeanPostProcessor instantiation, i.e. when a bean is not eligible for
	 * getting processed by all BeanPostProcessors.
	 */
	private static final class BeanPostProcessorChecker implements BeanPostProcessor {

		private static final Log logger = LogFactory.getLog(BeanPostProcessorChecker.class);

		private final ConfigurableListableBeanFactory beanFactory;

		private final int beanPostProcessorTargetCount;

		public BeanPostProcessorChecker(ConfigurableListableBeanFactory beanFactory, int beanPostProcessorTargetCount) {
			this.beanFactory = beanFactory;
			this.beanPostProcessorTargetCount = beanPostProcessorTargetCount;
		}

		@Override
		public Object postProcessBeforeInitialization(Object bean, String beanName) {
			return bean;
		}

		@Override
		// 后置处理器方法，用来判断那些 bean 不需要检测
		public Object postProcessAfterInitialization(Object bean, String beanName) {
			// beanPostProcessor 类型不检测
			// ROLE_INFRASTRUCTURE 为 spring 自己的bean,不检测
			if (!(bean instanceof BeanPostProcessor) && !isInfrastructureBean(beanName) &&
					this.beanFactory.getBeanPostProcessorCount() < this.beanPostProcessorTargetCount) {
				if (logger.isInfoEnabled()) {
					logger.info("Bean '" + beanName + "' of type [" + bean.getClass().getName() +
							"] is not eligible for getting processed by all BeanPostProcessors " +
							"(for example: not eligible for auto-proxying)");
				}
			}
			return bean;
		}

		private boolean isInfrastructureBean(@Nullable String beanName) {
			if (beanName != null && this.beanFactory.containsBeanDefinition(beanName)) {
				BeanDefinition bd = this.beanFactory.getBeanDefinition(beanName);
				return (bd.getRole() == RootBeanDefinition.ROLE_INFRASTRUCTURE);
			}
			return false;
		}
	}

}
