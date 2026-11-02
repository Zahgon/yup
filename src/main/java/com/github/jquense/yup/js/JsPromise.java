/*
 * The MIT License (MIT)
 * Copyright (c) 2014 Jason Quense
 *
 * Java port of yup (https://github.com/jquense/yup).
 */
package com.github.jquense.yup.js;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * A JavaScript promise with the ECMAScript job semantics: reactions run as jobs from one FIFO queue (per thread), a
 * promise resolved with another promise adopts its state two jobs later (a thenable's {@code then} is called one job
 * later), and nothing runs until {@link #drain()}.
 */
public final class JsPromise {
  private static final ThreadLocal<ArrayDeque<Runnable>> JOBS = ThreadLocal.withInitial(ArrayDeque::new);

  private enum State {
    PENDING,
    FULFILLED,
    REJECTED
  }

  private State state = State.PENDING;
  private Object result;
  private final List<Runnable> reactions = new ArrayList<>();

  private JsPromise() {}

  /**
   * {@code new Promise(executor)}: the executor runs now with the resolve and reject functions; a value it throws
   * rejects the promise.
   *
   * @param executor the executor
   * @return the promise
   */
  public static JsPromise create(BiConsumer<Function<Object, Void>, Function<Object, Void>> executor) {
    JsPromise promise = new JsPromise();
    boolean[] done = {false};
    Function<Object, Void> resolve = value -> {
      if (!done[0]) {
        done[0] = true;
        promise.resolve(value);
      }
      return null;
    };
    Function<Object, Void> reject = reason -> {
      if (!done[0]) {
        done[0] = true;
        promise.settle(State.REJECTED, reason);
      }
      return null;
    };
    try {
      executor.accept(resolve, reject);
    } catch (RuntimeException e) {
      reject.apply(JsException.valueOf(e));
    }
    return promise;
  }

  /**
   * {@code Promise.resolve(value)}: a promise is returned as it is.
   *
   * @param value any value
   * @return the promise
   */
  public static JsPromise resolved(Object value) {
    if (value instanceof JsPromise promise) {
      return promise;
    }
    JsPromise promise = new JsPromise();
    promise.resolve(value);
    return promise;
  }

  /**
   * {@code Promise.reject(reason)}.
   *
   * @param reason the rejection reason
   * @return the rejected promise
   */
  public static JsPromise rejected(Object reason) {
    JsPromise promise = new JsPromise();
    promise.settle(State.REJECTED, reason);
    return promise;
  }

  /**
   * Whether {@code typeof value?.then === 'function'}: a promise, or a plain object with a function {@code then}
   * member (the only objects here that can have one).
   *
   * @param value any value
   * @return whether the value is a thenable
   */
  public static boolean isThenable(Object value) {
    return value instanceof JsPromise || value instanceof JsObject object && object.get("then") instanceof JsFunction;
  }

  // The promise resolve function: adopts a promise's or a thenable's state through a job, fulfills with anything else.
  private void resolve(Object value) {
    if (value == this) {
      settle(State.REJECTED, JsError.typeError("Chaining cycle detected for promise #<Promise>"));
      return;
    }
    if (value instanceof JsPromise other) {
      enqueue(() -> other.then(v -> {
        resolve(v);
        return Js.UNDEFINED;
      }, r -> {
        settle(State.REJECTED, r);
        return Js.UNDEFINED;
      }));
      return;
    }
    if (value instanceof JsObject object && object.get("then") instanceof JsFunction then) {
      // NewPromiseResolveThenableJob: then(resolve, reject) with a fresh pair of resolving functions, a throw rejecting.
      enqueue(() -> {
        boolean[] done = {false};
        JsFunction onFulfilled = (self, args) -> {
          if (!done[0]) {
            done[0] = true;
            resolve(JsFunction.arg(args, 0));
          }
          return Js.UNDEFINED;
        };
        JsFunction onRejected = (self, args) -> {
          if (!done[0]) {
            done[0] = true;
            settle(State.REJECTED, JsFunction.arg(args, 0));
          }
          return Js.UNDEFINED;
        };
        try {
          then.call(object, onFulfilled, onRejected);
        } catch (RuntimeException e) {
          if (!done[0]) {
            done[0] = true;
            settle(State.REJECTED, JsException.valueOf(e));
          }
        }
      });
      return;
    }
    settle(State.FULFILLED, value);
  }

  private void settle(State newState, Object value) {
    if (state != State.PENDING) {
      return;
    }
    state = newState;
    result = value;
    for (Runnable reaction : reactions) {
      enqueue(reaction);
    }
    reactions.clear();
  }

  /**
   * {@code promise.then(onFulfilled, onRejected)}; a handler that throws rejects the derived promise.
   *
   * @param onFulfilled the fulfilment handler, or null to pass the value on
   * @param onRejected the rejection handler, or null to pass the reason on
   * @return the derived promise
   */
  public JsPromise then(Function<Object, Object> onFulfilled, Function<Object, Object> onRejected) {
    JsPromise derived = new JsPromise();
    Runnable reaction = () -> {
      Function<Object, Object> handler = state == State.FULFILLED ? onFulfilled : onRejected;
      if (handler == null) {
        if (state == State.FULFILLED) {
          derived.resolve(result);
        } else {
          derived.settle(State.REJECTED, result);
        }
        return;
      }
      Object value;
      try {
        value = handler.apply(result);
      } catch (RuntimeException e) {
        derived.settle(State.REJECTED, JsException.valueOf(e));
        return;
      }
      derived.resolve(value);
    };
    if (state == State.PENDING) {
      reactions.add(reaction);
    } else {
      enqueue(reaction);
    }
    return derived;
  }

  private static void enqueue(Runnable job) {
    JOBS.get().add(job);
  }

  /** Runs queued jobs until the queue is empty. */
  public static void drain() {
    ArrayDeque<Runnable> jobs = JOBS.get();
    Runnable job;
    while ((job = jobs.poll()) != null) {
      job.run();
    }
  }

  /** @return whether the promise is settled */
  public boolean isSettled() {
    return state != State.PENDING;
  }

  /** @return whether the promise is rejected */
  public boolean isRejected() {
    return state == State.REJECTED;
  }

  /** @return the fulfilment value or the rejection reason */
  public Object result() {
    return result;
  }

  /**
   * Runs every job and returns the outcome, as {@code await} would after the event loop settles.
   *
   * @return the fulfilment value
   * @throws NeverSettled when no job is left and the promise is still pending
   * @throws RuntimeException the rejection reason, thrown
   */
  public Object await() {
    drain();
    if (state == State.REJECTED) {
      throw JsException.of(result);
    }
    if (state == State.PENDING) {
      throw new NeverSettled();
    }
    return result;
  }

  /** An awaited promise that can never settle (a thenable that never calls back): Node would just exit. */
  public static final class NeverSettled extends RuntimeException {
    private static final long serialVersionUID = 1L;

    NeverSettled() {
      super("the promise never settled", null, false, false);
    }
  }
}
