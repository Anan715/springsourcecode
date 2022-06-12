package factorytest;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MyClassPathXMLApplicationContext extends ClassPathXmlApplicationContext {


	@Override
	protected void initPropertySources() {
		super.initPropertySources();
	}

	@Override
	protected void customizeBeanFactory(DefaultListableBeanFactory beanFactory) {
		super.customizeBeanFactory(beanFactory);
	}
}
