package beantest;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class TestClass {
	public static void main(String[] args) {
//		AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
//		String[] beanDefinitionNames = applicationContext.getBeanDefinitionNames();
		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
		B b = context.getBean(B.class);
		b.sout();

		Student student = (Student) context.getBean("studentFactoryBean");
		System.out.println("student.getUserName() = " + student.getUserName());


		// FactoryBean 接口测试
//		ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
//		B b =(B) context.getBean("a");
//		b.sout();
//		A a =(A) context.getBean("&a");
//		a.sout();


	}
}
