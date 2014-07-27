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
* Bundlized for OSGi
* Active Object support
* Core asynchronous algorithms: reduce, map-reduce, while-do

## Requirements
This library requires Java 8 SE or later.

## Alternatives
AsyncJ doesn't provide implementation of actors, agents or messaging subsystem and require Java 8 or later. If AsyncJ doesn't fit your 
requirements you can choose the following alternatives:

* [async4j](https://github.com/amah/async) - asynchronous programming library that provides a set of callback based constructs
* [promise4j](https://github.com/tehsenaus/promise4j) - completely type-safe implementation of Promises for Java
* [Akka](http://akka.io) - building scalable applications using actors and agents