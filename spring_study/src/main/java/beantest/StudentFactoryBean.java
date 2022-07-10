package beantest;

import org.springframework.beans.factory.FactoryBean;

public class StudentFactoryBean implements FactoryBean<Student> {
	@Override
	public Student getObject() throws Exception {
		return new Student("张三");
	}

	@Override
	public Class<?> getObjectType() {
		return Student.class;
	}

	@Override
	public boolean isSingleton() {
		// 决定单例多例
		return false;
	}
}
