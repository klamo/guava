/*
 * Copyright (C) 2005 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.common.testing;

import com.google.common.annotations.Beta;
import com.google.common.base.Function;
import com.google.common.base.Functions;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.common.collect.ClassToInstanceMap;
import com.google.common.collect.Lists;
import com.google.common.collect.MutableClassToInstanceMap;

import junit.framework.Assert;
import junit.framework.AssertionFailedError;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import javax.annotation.Nullable;

/**
 * A test utility that verifies that your methods throw {@link
 * NullPointerException} or {@link UnsupportedOperationException} whenever any
 * of their parameters are null. To use it, you must first provide valid default
 * values for the parameter types used by the class.
 *
 * @author Kevin Bourrillion
 * @since 10.0
 */
@Beta
public final class NullPointerTester {

  private final ClassToInstanceMap<Object> defaults =
      MutableClassToInstanceMap.create();
  private final List<Member> ignoredMembers = Lists.newArrayList();

  public NullPointerTester() {
    setCommonDefaults();
  }

  private final void setCommonDefaults() {
    // mutable types
    setDefault(Appendable.class, new StringBuilder());
    setDefault(Throwable.class, new Exception());

    // The following 4 aren't really safe generically
    // For example, Comparable<String> can't be 0.
    setDefault(Class.class, Class.class);
    setDefault(Function.class, Functions.identity());
    setDefault(Supplier.class, Suppliers.ofInstance(1));

  }

  /**
   * Sets a default value that can be used for any parameter of type
   * {@code type}. Returns this object.
   */
  public <T> NullPointerTester setDefault(Class<T> type, T value) {
    defaults.put(type, value);
    return this;
  }

  /**
   * Ignore a member (constructor or method) in testAllXxx methods. Returns
   * this object.
   */
  public NullPointerTester ignore(Member member) {
    ignoredMembers.add(member);
    return this;
  }

  /**
   * Runs {@link #testConstructor} on every constructor in class {@code c} that
   * has at least {@code minimalVisibility}.
   */
  public void testConstructors(Class<?> c, Visibility minimalVisibility) {
    for (Constructor<?> constructor : c.getDeclaredConstructors()) {
      if (minimalVisibility.isVisible(constructor) && !isIgnored(constructor)) {
        testConstructor(constructor);
      }
    }
  }

  /**
   * Runs {@link #testConstructor} on every public constructor in class {@code
   * c}.
   */
  public void testAllPublicConstructors(Class<?> c) {
    testConstructors(c, Visibility.PUBLIC);
  }

  /**
   * Runs {@link #testMethod} on every static method declared in class {@code c}
   * that has at least {@code minimalVisibility}.
   */
  public void testStaticMethods(Class<?> c, Visibility minimalVisibility) {
    for (Method method : c.getDeclaredMethods()) {
      if (minimalVisibility.isVisible(method)
          && isStatic(method)
          && !isIgnored(method)) {
        testMethod(null, method);
      }
    }
  }

  /**
   * Runs {@link #testMethod} on every public static method declared by class
   * {@code c}.
   */
  public void testAllPublicStaticMethods(Class<?> c) {
    testStaticMethods(c, Visibility.PUBLIC);
  }

  /**
   * Runs {@link #testMethod} on every instance method declared by the class
   * of {@code instance} with at least {@code minimalVisibility}.
   */
  public void testInstanceMethods(
      Object instance, Visibility minimalVisibility) {
    Class<?> c = instance.getClass();
    for (Method method : c.getDeclaredMethods()) {
      if (minimalVisibility.isVisible(method)
          && !isStatic(method)
          && !isIgnored(method)) {
        testMethod(instance, method);
      }
    }
  }

  /**
   * Runs {@link #testMethod} on every public instance method declared by the
   * class of {@code instance}.
   */
  public void testAllPublicInstanceMethods(Object instance) {
    testInstanceMethods(instance, Visibility.PUBLIC);
  }

  /**
   * Verifies that {@code method} produces a {@link NullPointerException}
   * or {@link UnsupportedOperationException} whenever <i>any</i> of its
   * non-{@link Nullable} parameters are null.
   *
   * @param instance the instance to invoke {@code method} on, or null if
   *     {@code method} is static
   */
  public void testMethod(Object instance, Method method) {
    Class<?>[] types = method.getParameterTypes();
    for (int nullIndex = 0; nullIndex < types.length; nullIndex++) {
      testMethodParameter(instance, method, nullIndex);
    }
  }

  /**
   * Verifies that {@code ctor} produces a {@link NullPointerException} or
   * {@link UnsupportedOperationException} whenever <i>any</i> of its
   * non-{@link Nullable} parameters are null.
   */
  public void testConstructor(Constructor<?> ctor) {
    Class<?>[] types = ctor.getParameterTypes();
    for (int nullIndex = 0; nullIndex < types.length; nullIndex++) {
      testConstructorParameter(ctor, nullIndex);
    }
  }

  /**
   * Verifies that {@code method} produces a {@link NullPointerException} or
   * {@link UnsupportedOperationException} when the parameter in position {@code
   * paramIndex} is null.  If this parameter is marked {@link Nullable}, this
   * method does nothing.
   *
   * @param instance the instance to invoke {@code method} on, or null if
   *     {@code method} is static
   */
  public void testMethodParameter(Object instance, final Method method,
      int paramIndex) {
    method.setAccessible(true);
    testFunctorParameter(instance, new Functor() {
        @Override public Class<?>[] getParameterTypes() {
          return method.getParameterTypes();
        }
        @Override public Annotation[][] getParameterAnnotations() {
          return method.getParameterAnnotations();
        }
        @Override public void invoke(Object instance, Object[] params)
            throws InvocationTargetException, IllegalAccessException {
          method.invoke(instance, params);
        }
        @Override public String toString() {
          return method.getName()
              + "(" + Arrays.toString(getParameterTypes()) + ")";
        }
      }, paramIndex, method.getDeclaringClass());
  }

  /**
   * Verifies that {@code ctor} produces a {@link NullPointerException} or
   * {@link UnsupportedOperationException} when the parameter in position {@code
   * paramIndex} is null.  If this parameter is marked {@link Nullable}, this
   * method does nothing.
   */
  public void testConstructorParameter(final Constructor<?> ctor,
      int paramIndex) {
    ctor.setAccessible(true);
    testFunctorParameter(null, new Functor() {
        @Override public Class<?>[] getParameterTypes() {
          return ctor.getParameterTypes();
        }
        @Override public Annotation[][] getParameterAnnotations() {
          return ctor.getParameterAnnotations();
        }
        @Override public void invoke(Object instance, Object[] params)
            throws InvocationTargetException, IllegalAccessException,
            InstantiationException {
          ctor.newInstance(params);
        }
      }, paramIndex, ctor.getDeclaringClass());
  }

  /** Visibility of any method or constructor. */
  public enum Visibility {

    PACKAGE {
      @Override boolean isVisible(int modifiers) {
        return !Modifier.isPrivate(modifiers);
      }
    },

    PROTECTED {
      @Override boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers) || Modifier.isProtected(modifiers);
      }
    },

    PUBLIC {
      @Override boolean isVisible(int modifiers) {
        return Modifier.isPublic(modifiers);
      }
    };

    abstract boolean isVisible(int modifiers);

    /**
     * Returns {@code true} if {@code member} is visible under {@code this}
     * visibility.
     */
    final boolean isVisible(Member member) {
      return isVisible(member.getModifiers());
    }
  }

  /**
   * Verifies that {@code func} produces a {@link NullPointerException} or
   * {@link UnsupportedOperationException} when the parameter in position {@code
   * paramIndex} is null.  If this parameter is marked {@link Nullable}, this
   * method does nothing.
   *
   * @param instance the instance to invoke {@code func} on, or null if
   *     {@code func} is static
   */
  private void testFunctorParameter(Object instance, Functor func,
      int paramIndex, Class<?> testedClass) {
    if (parameterIsPrimitiveOrNullable(func, paramIndex)) {
      return; // there's nothing to test
    }
    Object[] params = buildParamList(func, paramIndex);
    try {
      func.invoke(instance, params);
      Assert.fail("No exception thrown from " + func +
          Arrays.toString(params) + " for " + testedClass);
    } catch (InvocationTargetException e) {
      Throwable cause = e.getCause();
      if (cause instanceof NullPointerException ||
          cause instanceof UnsupportedOperationException) {
        return;
      }
      AssertionFailedError error = new AssertionFailedError(
          "wrong exception thrown from " + func + ": " + cause);
      error.initCause(cause);
      throw error;
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InstantiationException e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean parameterIsPrimitiveOrNullable(
      Functor func, int paramIndex) {
    if (func.getParameterTypes()[paramIndex].isPrimitive()) {
      return true;
    }
    Annotation[] annotations = func.getParameterAnnotations()[paramIndex];
    for (Annotation annotation : annotations) {
      if (annotation instanceof Nullable) {
        return true;
      }
    }
    return false;
  }

  private Object[] buildParamList(Functor func, int indexOfParamToSetToNull) {
    Class<?>[] types = func.getParameterTypes();
    Object[] params = new Object[types.length];

    for (int i = 0; i < types.length; i++) {
      if (i != indexOfParamToSetToNull) {
        Class<?> type = types[i];
        params[i] = getDefaultValue(type);
        if (!parameterIsPrimitiveOrNullable(func, i)) {
          Assert.assertTrue("No default value found for " + type.getName(),
              params[i] != null);
        }
      }
    }
    return params;
  }

  private <T> T getDefaultValue(Class<T> type) {
    T value = defaults.getInstance(type);
    if (value != null) {
      return value;
    }
    return NullValues.get(type);
  }

  private interface Functor {
    Class<?>[] getParameterTypes();
    Annotation[][] getParameterAnnotations();
    void invoke(Object o, Object[] params)
        throws InvocationTargetException, IllegalAccessException,
            InstantiationException;
  }

  private static boolean isStatic(Member member) {
    return Modifier.isStatic(member.getModifiers());
  }

  private boolean isIgnored(Member member) {
    return member.isSynthetic() || ignoredMembers.contains(member);
  }
}
