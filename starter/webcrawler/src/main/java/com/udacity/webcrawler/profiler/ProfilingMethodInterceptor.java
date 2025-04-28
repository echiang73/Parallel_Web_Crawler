package com.udacity.webcrawler.profiler;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * A method interceptor that checks whether {@link Method}s are annotated with the {@link Profiled}
 * annotation. If they are, the method interceptor records how long the method invocation took.
 */
final class ProfilingMethodInterceptor implements InvocationHandler {

  private final Clock clock;
  private Object delegate;
  private ProfilingState state;

  ProfilingMethodInterceptor(Clock clock, Object delegate, ProfilingState state) {
    this.clock = Objects.requireNonNull(clock);
    this.delegate = Objects.requireNonNull(delegate);
    this.state = Objects.requireNonNull(state);
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    // This method interceptor should inspect the called method to see if it is a profiled method.
    Object profiledMethod;
    Instant startTime = null;
    boolean isProfiledMethod = method.getAnnotation(Profiled.class) != null;

    // For profiled methods, the interceptor should record the start time,
    if (isProfiledMethod) {
      startTime = clock.instant();
    }

    // then invoke the method using the object that is being profiled.
    try {
      /*Think carefully about how the proxy should behave for the java.lang.Object#equals(Object) method.
      If 1) method name is .equals(), 2) has one parameter, 3) parameter is an Object data type,
      then invoke equals on the delegate Object, otherwise invoke method on delegate Object
      */
      if (method.getName().equals("equals") && method.getParameterCount() == 1
              && method.getParameterTypes()[0].equals(Object.class)) {
        profiledMethod = delegate.equals(args[0]);
      } else {
        profiledMethod = method.invoke(delegate, args);
      }
    } catch (InvocationTargetException e) {
      throw e.getTargetException();
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } finally {
    // Finally, for profiled methods, the interceptor should record how long the method call took, using the ProfilingState methods.
      if (isProfiledMethod) {
        Instant stopTime = clock.instant();
        Duration duration = Duration.between(startTime, stopTime);
        state.record(delegate.getClass(), method, duration);
      }
    }
    return profiledMethod;
  }
}
