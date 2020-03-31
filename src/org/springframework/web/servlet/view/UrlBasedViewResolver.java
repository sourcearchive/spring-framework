/*
 * Copyright 2002-2004 the original author or authors.
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

package org.springframework.web.servlet.view;

import java.util.Locale;

import org.springframework.beans.BeanUtils;
import org.springframework.web.servlet.View;

/**
 * Simple implementation of ViewResolver that allows for direct resolution of
 * symbolic view names to URLs, without explicit mapping definition. This is
 * appropriate if your symbolic names match the names of your view resources
 * in a straightforward manner, without the need for arbitrary mappings.
 *
 * <p>Supports AbstractUrlBasedView subclasses like InternalResourceView and
 * VelocityView. The view class for all views generated by this resolver can
 * be specified via setViewClass.
 *
 * <p>View names can either be resource URLs themselves, or get augmented by a
 * specified prefix and/or suffix. Exporting an attribute that holds the
 * RequestContext to all views is explicitly supported.
 *
 * <p>Example: prefix="/WEB-INF/jsp/", suffix=".jsp", viewname="test" ->
 * "/WEB-INF/jsp/test.jsp"
 *
 * <p>Note: This class does not support localized resolution, i.e. resolving
 * a symbolic view name to different resources depending on the current locale.
 *
 * @author Juergen Hoeller
 * @since 13.12.2003
 * @see #setViewClass
 * @see #setPrefix
 * @see #setSuffix
 * @see #setRequestContextAttribute
 * @see AbstractUrlBasedView
 */
public class UrlBasedViewResolver extends AbstractCachingViewResolver {

	private Class viewClass;

	private String prefix = "";

	private String suffix = "";

	private String contentType;

	private String requestContextAttribute;

	/**
	 * Set the view class that should be used to create views.
	 * @param viewClass class that is assignable to InternalResourceView
	 */
	public void setViewClass(Class viewClass) {
		if (viewClass == null || !requiredViewClass().isAssignableFrom(viewClass)) {
			throw new IllegalArgumentException("Given View class [" + viewClass.getName() + "] is not of type [" +
																				 requiredViewClass().getName() + "]");
		}
		this.viewClass = viewClass;
	}

	/**
	 * Return the required type of view for this resolver.
	 * This implementation returns AbstractUrlBasedView.
	 * @see AbstractUrlBasedView
	 */
	protected Class requiredViewClass() {
		return AbstractUrlBasedView.class;
	}

	/**
	 * Set the prefix that gets applied to view names when building a URL.
	 * @param prefix view name prefix
	 */
	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	/**
	 * Set the suffix that gets applied to view names when building a URL.
	 * @param suffix view name suffix
	 */
	public void setSuffix(String suffix) {
		this.suffix = suffix;
	}

	/**
	 * Set the content type for all views.
	 * May be ignored by view classes if the view itself is assumed
	 * to set the content type, e.g. in case of JSPs.
	 * @param contentType the content type
	 */
	public void setContentType(String contentType) {
		this.contentType = contentType;
	}

	/**
	 * Set the name of the RequestContext attribute for all views.
	 * @param requestContextAttribute name of the RequestContext attribute
	 */
	public void setRequestContextAttribute(String requestContextAttribute) {
		this.requestContextAttribute = requestContextAttribute;
	}

	/**
	 * This implementation returns just the view name,
	 * as InternalResourceViewResolver doesn't support localized resolution.
	 */
	protected String getCacheKey(String viewName, Locale locale) {
		return viewName;
	}

	protected View loadView(String viewName, Locale locale) {
		AbstractUrlBasedView view = (AbstractUrlBasedView) BeanUtils.instantiateClass(this.viewClass);
		view.setBeanName(viewName);
		view.setUrl(this.prefix + viewName + this.suffix);
		if (this.contentType != null) {
			view.setContentType(this.contentType);
		}
		view.setRequestContextAttribute(this.requestContextAttribute);
		return view;
	}

}
