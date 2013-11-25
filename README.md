mpim
====

Multiple Proxy Instant Messaging

The goal of the project was to build a lightweight proxy to handle multiple instant messaging accounts under a single XMPP account. 
There're two main components in this project: the core and the plugin infrastructure.



The Core
========

It's responsible for handling the connection between the legacy IM like MSN or IRC or whatever plugin is implemented, and the XMPP connection.
The plugin infrastructure is implemented in a threaded environment, so that each plugin can be used independently of each others.

Plugins
=======

To create a plugin one must extend the plugin base class.

