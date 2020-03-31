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

package org.springframework.web.portlet.context;

import java.util.Locale;
import java.util.Map;

import javax.portlet.PortletRequest;

import org.springframework.web.context.request.WebRequest;

/**
 * WebRequest adapter for a PortletRequest.
 *
 * @author Juergen Hoeller
 * @since 2.0
 */
public class PortletWebRequest extends PortletRequestAttributes implements WebRequest {

	/**
	 * Create a new PortletWebRequest instance for the given request.
	 * @param request current portlet request
	 */
	public PortletWebRequest(PortletRequest request) {
		super(request);
	}

	public String getParameter(String paramName) {
		return getRequest().getParameter(paramName);
	}

	public String[] getParameterValues(String paramName) {
		return getRequest().getParameterValues(paramName);
	}

	public Map getParameterMap() {
		return getRequest().getParameterMap();
	}

	public Locale getLocale() {
		return getRequest().getLocale();
	}

}
