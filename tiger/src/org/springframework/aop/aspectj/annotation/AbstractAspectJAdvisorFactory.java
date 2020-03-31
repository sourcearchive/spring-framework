/*
 * Copyright 2002-2006 the original author or authors.
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

package org.springframework.aop.aspectj.annotation;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.aspectj.lang.annotation.After;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.AjType;
import org.aspectj.lang.reflect.AjTypeSystem;
import org.aspectj.lang.reflect.PerClauseKind;

import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.framework.AopConfigException;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.PrioritizedParameterNameDiscoverer;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.util.StringUtils;

/**
 * Abstract base class for factories that can create Spring AOP Advisors
 * given AspectJ classes from classes honoring the AspectJ 5 annotation syntax.
 *
 * <p>This class handles annotation parsing and validation functionality.
 * It does not actually generate Spring AOP Advisors, which is deferred to subclasses.
 *
 * @author Rod Johnson
 * @author Adrian Colyer
 * @since 2.0
 */
public abstract class AbstractAspectJAdvisorFactory implements AspectJAdvisorFactory {
	
	private static final String AJC_MAGIC = "ajc$";
	
	protected static final ParameterNameDiscoverer ASPECTJ_ANNOTATION_PARAMETER_NAME_DISCOVERER =
			new AspectJAnnotationParameterNameDiscoverer();


	/**
	 * Find and return the first AspectJ annotation on the given method
	 * (there <i>should</i> only be one anyway...)
	 */
	protected static AspectJAnnotation findAspectJAnnotationOnMethod(Method aMethod) {
		Class<? extends Annotation>[] classesToLookFor = (Class<? extends Annotation>[]) new Class[] {
					Before.class, 
					Around.class, 
					After.class, 
					AfterReturning.class, 
					AfterThrowing.class, 
					Pointcut.class
				};
		for (Class<? extends Annotation> c : classesToLookFor) {
			AspectJAnnotation foundAnnotation = findAnnotation(aMethod, c);
			if (foundAnnotation != null) {
				return foundAnnotation;
			}
		}
		return null;
	}
	
	private static <A extends Annotation> AspectJAnnotation<A> findAnnotation(Method method, Class<A> toLookFor) {
		A result = AnnotationUtils.findAnnotation(method, toLookFor);
		if (result != null) {
			return new AspectJAnnotation<A>(result);
		}
		else {
			return null;
		}
	}


	/** Logger available to subclasses */
	protected final Log logger = LogFactory.getLog(getClass());
	
	protected final ParameterNameDiscoverer parameterNameDiscoverer;
	
	
	protected AbstractAspectJAdvisorFactory() {
		PrioritizedParameterNameDiscoverer prioritizedParameterNameDiscoverer = new PrioritizedParameterNameDiscoverer();
		prioritizedParameterNameDiscoverer.addDiscoverer(ASPECTJ_ANNOTATION_PARAMETER_NAME_DISCOVERER);
		this.parameterNameDiscoverer = prioritizedParameterNameDiscoverer;
	}

	public boolean isAspect(Class<?> clazz) {
		boolean couldBeAtAspectJAspect = AjTypeSystem.getAjType(clazz).isAspect();
		if (!couldBeAtAspectJAspect) {
			return false;
		} 
		else {
			// we know it's an aspect, but we don't know whether it is an 
			// @AspectJ aspect or a code style aspect.
			// This is an *unclean* test whilst waiting for AspectJ to provide
			// us with something better
			Method[] methods = clazz.getDeclaredMethods();
			for(Method m : methods) {
				if (m.getName().startsWith(AJC_MAGIC)) {
					// must be a code style aspect
					return false;
				}
			}
			return true;
		}
	}

	public void validate(Class<?> aspectClass) throws AopConfigException {
		// If the parent has the annotation and isn't abstract it's an error
		if (aspectClass.getSuperclass().getAnnotation(Aspect.class) != null &&
				!Modifier.isAbstract(aspectClass.getSuperclass().getModifiers())) {
			throw new AopConfigException(aspectClass.getName() + " cannot extend concrete aspect " + 
					aspectClass.getSuperclass().getName());
		}

		AjType<?> ajType = AjTypeSystem.getAjType(aspectClass);
		if (!ajType.isAspect()) {
			throw new NotAnAtAspectException(aspectClass);
		}
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflow instantiation model: " +
					"This is not supported in Spring AOP");
		}
		if (ajType.getPerClause().getKind() == PerClauseKind.PERCFLOWBELOW) {
			throw new AopConfigException(aspectClass.getName() + " uses percflowbelow instantiation model: " +
					"This is not supported in Spring AOP");
		}	
	}

	/**
	 * The pointcut and advice annotations both have an "argNames" member which contains a 
	 * comma-separated list of the argument names. We use this (if non-empty) to build the
	 * formal parameters for the pointcut.
	 */
	protected AspectJExpressionPointcut createPointcutExpression(
			Method annotatedMethod, Class declarationScope, String[] pointcutParameterNames) {

		Class<?> [] pointcutParameterTypes = new Class<?>[0];
		if (pointcutParameterNames != null) {
			pointcutParameterTypes = extractPointcutParameterTypes(pointcutParameterNames,annotatedMethod);
		}
		
		AspectJExpressionPointcut ajexp =
				new AspectJExpressionPointcut(declarationScope,pointcutParameterNames,pointcutParameterTypes);
		ajexp.setLocation(annotatedMethod.toString());
		return ajexp;
	}
	
	/**
	 * Create the pointcut parameters needed by aspectj based on the given argument names
	 * and the argument types that are available from the adviceMethod. Needs to take into
	 * account (ignore) any JoinPoint based arguments as these are not pointcut context but
	 * rather part of the advice execution context (thisJoinPoint, thisJoinPointStaticPart)
	 */
	private Class<?>[] extractPointcutParameterTypes(String[] argNames, Method adviceMethod) {
		Class<?>[] ret = new Class<?>[argNames.length];
		Class<?>[] paramTypes = adviceMethod.getParameterTypes();
		if (argNames.length > paramTypes.length) {
			// TODO Spring logging here??
			throw new IllegalStateException("Expecting at least " + argNames.length + 
					     " arguments in the advice declaration, but only found " +
					     paramTypes.length);
		}
		// make the simplifying assumption for now that all of the JoinPoint based arguments
		// come first in the advice declaration
		int typeOffset = paramTypes.length - argNames.length;
		for (int i = 0; i < ret.length; i++) {
			ret[i] = paramTypes[i+typeOffset];			
		}
		return ret;
	}


	protected enum AspectJAnnotationType {
		AtPointcut,
		AtBefore,
		AtAfter,
		AtAfterReturning,
		AtAfterThrowing,
		AtAround
	};


	/**
	 * Class modelling an AspectJ annotation, exposing its type enumeration and
	 * pointcut String.
	 */
	protected static class AspectJAnnotation<A extends Annotation> {

		private static Map<Class,AspectJAnnotationType> annotationTypes = new HashMap<Class,AspectJAnnotationType>();

		private static final String[] EXPRESSION_PROPERTIES = new String[]{"value", "pointcut"};

		static {
			annotationTypes.put(Pointcut.class,AspectJAnnotationType.AtPointcut);
			annotationTypes.put(After.class,AspectJAnnotationType.AtAfter);
			annotationTypes.put(AfterReturning.class,AspectJAnnotationType.AtAfterReturning);
			annotationTypes.put(AfterThrowing.class,AspectJAnnotationType.AtAfterThrowing);
			annotationTypes.put(Around.class,AspectJAnnotationType.AtAround);
			annotationTypes.put(Before.class,AspectJAnnotationType.AtBefore);
		}

		private final A annotation;
		private AspectJAnnotationType annotationType;
		private final String expression;
		private final String argNames;

		public AspectJAnnotation(A aspectjAnnotation) {
			this.annotation = aspectjAnnotation;
			for(Class c : annotationTypes.keySet()) {
				if (c.isInstance(this.annotation)) {
					this.annotationType = annotationTypes.get(c);
					break;
				}
			}
			if (this.annotationType == null) {
				throw new IllegalStateException("unknown annotation type: " + this.annotation.toString());
			}

			// We know these methods exist with the same name on each object,
			// but need to invoke them reflectively as there isn't a common interfaces
			try {
				this.expression = resolveExpression();
				this.argNames = (String) annotation.getClass().getMethod("argNames", (Class[]) null).
					invoke(this.annotation);
			}
			catch (Exception ex) {
				throw new IllegalArgumentException(aspectjAnnotation + " cannot be an AspectJ annotation", ex);
			}
		}

		private String resolveExpression() throws IllegalAccessException, InvocationTargetException, NoSuchMethodException {
			String expression = null;
			for (int i = 0; i < EXPRESSION_PROPERTIES.length; i++) {
				String methodName = EXPRESSION_PROPERTIES[i];
				Method method;
				try {
					method = annotation.getClass().getDeclaredMethod(methodName);
				}
				catch (NoSuchMethodException ex) {
					method = null;
				}

				if (method != null) {
					String candidate = (String) method.invoke(this.annotation);

					if (StringUtils.hasText(candidate)) {
						expression = candidate;
					}
				}
			}
			return expression;
		}

		public AspectJAnnotationType getAnnotationType() {
			return this.annotationType;
		}

		public A getAnnotation() {
			return this.annotation;
		}

		public String getPointcutExpression() {
			return this.expression;
		}

		public String getArgNames() {
			return this.argNames;
		}

		public String toString() {
			return this.annotation.toString();
		}
	}


	private static class AspectJAnnotationParameterNameDiscoverer implements ParameterNameDiscoverer {

		public String[] getParameterNames(Method m) {
			if (m.getParameterTypes().length == 0) {
				return new String[0];
			}
			
			AspectJAnnotation annotation = findAspectJAnnotationOnMethod(m);
			if (annotation == null) {
				return null;
			}
			
			StringTokenizer strTok = new StringTokenizer(annotation.getArgNames(),",");
			if (strTok.countTokens() > 0) {
				String[] ret = new String[strTok.countTokens()];
				for (int i = 0; i < ret.length; i++) {
					ret[i] = strTok.nextToken();
				}
				
				return ret;
			} else { 
				return null; 				
			}
		}
		
		public String[] getParameterNames(Constructor ctor) {
			throw new UnsupportedOperationException("Spring AOP cannot handle constructor advice");
		}
	}

}
