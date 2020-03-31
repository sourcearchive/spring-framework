/*
 * Copyright 2002-2005 the original author or authors.
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

import org.springframework.aop.aspectj.AspectInstanceFactory;

/**
 * Subinterface of AspectInstanceFactory that returns AspectMetadata
 * associated with AspectJ 5 annotated classes.
 *
 * <p>Ideally, AspectInstanceFactory would include this method, but because
 * AspectMetadata uses Java 5-only AJType, we need to split out these interfaces.
 *
 * @author Rod Johnson
 * @since 2.0
 */
public interface MetadataAwareAspectInstanceFactory extends AspectInstanceFactory {
	
	AspectMetadata getAspectMetadata();

}
