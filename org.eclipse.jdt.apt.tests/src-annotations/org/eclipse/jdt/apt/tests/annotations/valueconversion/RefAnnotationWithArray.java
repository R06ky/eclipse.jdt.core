/*******************************************************************************
 * Copyright (c) 2005, 2007 BEA Systems, Inc.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *******************************************************************************/
package org.eclipse.jdt.apt.tests.annotations.valueconversion;

public @interface RefAnnotationWithArray {
	boolean[] booleans(); 
	byte[] bytes();
	short[] shorts(); 
	int[] ints();
	long[] longs();
	float[] floats();
	double[] doubles();
	char[] chars();
	String str() default "string";
}
