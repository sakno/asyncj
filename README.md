AsyncJ Framework
======

Flexible and super lightweight library for asynchronous non-blocking programming using Future pattern, callbacks (NodeJS-style) 
and [active objects](http://en.wikipedia.org/wiki/Active_object). 

## Motivation
The first aim of this library is to provide lightweight implementation of [Promise](http://en.wikipedia.org/wiki/Futures_and_promises) pattern for
Java 8. Also, the library provides support for Java [Future](http://docs.oracle.com/javase/8/docs/api/java/util/concurrent/Future.html) 
pipelining.

The second aim is to demonstrate Java 8 features, such as [Lambda Expression](http://docs.oracle.com/javase/tutorial/java/javaOO/lambdaexpressions.html),
[Method Reference](http://docs.oracle.com/javase/tutorial/java/javaOO/methodreferences.html)

## Features
AsyncJ is very lightweight thing. The implementation consists of 20 classes (approximately) from which 4 classes intended for use from user code.
Despite the simple implementation, AsyncJ API is very powerful and provides the following features:

* Promise pipelining
* OSGi compatible
* Active Object support
* Core asynchronous algorithms: reduce, map-reduce, while-do

### Examples
Promise pipelining:
```java
final AsyncResult<Integer> ar = AsyncUtils.getGlobalScheduler().submit(() -> 42);
final Integer result = ar.then(i -> i * 2).then(i -> i + 10).get(); 
```

Compatibility with Java Future:
```java
final Future<Integer> f = AsyncUtils.getGlobalScheduler().submit(() -> 42).then(i -> i + 1); 
```

Asynchronous try-catch:
```java
p.then((Integer i) -> Integer.toString(i + 1), (Exception err) -> err.toString());
```

Callbacks:
```java
public void sum(final AsyncCallback<Integer> callback){
    p.onCompleted((i, err1)->{
      c.onCompleted((g, err2)->{
        if(err2 != null) callback.invoke(null, err2);
        else callback.invoke(i + g, null);
      });
    });
}
```

## Requirements
This library requires Java 8 SE or later.

## Alternatives
AsyncJ doesn't provide implementation of actors, agents or messaging subsystem and require Java 8 or later. If AsyncJ doesn't fit your 
requirements you can choose the following alternatives:

* [async4j](https://github.com/amah/async) - asynchronous programming library that provides a set of callback based constructs
* [promise4j](https://github.com/tehsenaus/promise4j) - completely type-safe implementation of Promises for Java
* [Akka](http://akka.io) - building scalable applications using actors and agents
* [JavaAsync](http://www.playframework.com/documentation/2.0/JavaAsync) as a part of Play! Framework
