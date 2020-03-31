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

package org.springframework.jdbc.core;

/**
 * Extension of the BatchPreparedStatementSetter interface that adds
 * a batch exhaustion check.
 *
 * <p>This interface allows you to signal the end of a batch rather than
 * having to determine the exact batch size upfront. Batch size is still
 * being honored but it is now the maximum size of the batch.
 *
 * <p>The <code>isBatchExhausted</code> method is called after each call to
 * <code>setValues</code> to determine whether there were some values added
 * or if the batch was determined to be complete and no additional values
 * were provided during the last call to <code>setValues</code>.
 *
 * <p>Consider extending the AbstractInterruptibleBatchPreparedStatementSetter
 * base class instead of implementing this interface directly, having a
 * single callback method that combines the check for available values
 * and the setting of those.
 *
 * @author Thomas Risberg
 * @since 2.0
 * @see JdbcTemplate#batchUpdate(String, BatchPreparedStatementSetter)
 * @see org.springframework.jdbc.core.support.AbstractInterruptibleBatchPreparedStatementSetter
 */
public interface InterruptibleBatchPreparedStatementSetter extends BatchPreparedStatementSetter {

	/**
	 * Return whether the batch is complete, that is, whether there were no
	 * additional values added during the last <code>setValues</code> call.
	 * <p><b>NOTE:</b> If this method returns <code>true</code>, any parameters
	 * that might have been set during the last <code>setValues</code> call will
	 * be ignored! Make sure that you set a corresponding internal flag if you
	 * detect exhaustion <i>at the beginning</i> of your <code>setValues</code>
	 * implementation, letting this method return <code>true</code> based on the flag.
	 * @param i index of the statement we're issuing in the batch, starting from 0
	 * @see #setValues
	 */
	boolean isBatchExhausted(int i);

}
