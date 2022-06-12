package beantest;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.stereotype.Component;

//@Component
public class A implements FactoryBean<B> {
	public A(){
		System.out.println("A 构造方法调用......");
	}

	public void sout(){
		System.out.println("A Bean");
	}

	@Override
	public B getObject() throws Exception {
		return new B();
	}

	@Override
	public Class<?> getObjectType() {
		return B.class;
	}

	@Override
	public boolean isSingleton() {
		return true;
	}
}
