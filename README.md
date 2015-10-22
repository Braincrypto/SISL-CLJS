# webdasher

A ClojureScript implementation of the SISL task for the web/MTurk.

## Dependencies

- Java 1.8
- Leiningen 2.0

## Setup

To get an interactive development environment run:

    lein figwheel

and open your browser at [localhost:3449](http://localhost:3449/).
This will auto compile and send all changes to the browser without the
need to reload. After the compilation process is complete, you will
get a Browser Connected REPL. An easy way to try it is:

    (js/alert "Am I connected?")

and you should see an alert in the browser window.

To clean all compiled files:

    lein clean

To create a production build run:

    lein cljsbuild once min

And open your browser in `resources/public/index.html`. You will not
get live reloading, nor a REPL. 

## Server Side

The development environment comes with a minimal implementation of a server
that accepts all data and discards it, only keeping track of what session IDs
it has already given out. Data is submitted to the server in JSON form, to the
following URLs:

"new-session.php" requests a new session. It expects an object of the form:

    { "browser-info": <user agent string> }

and returns

    { "success": true, "session-id": <integer> }

Where "session-id" is an integer that will be used to tag future uploads as
being part of the same logging session.

- "upload-data.php" uploads part of a log. It expects an object of the form:

    { "session": <session-id>,
      "data": [ <list of json objects representing events> ] }

and returns

    { "success": true }

Finally, "finish-session.php" closes out a session, after which no more data
will be written. It expects an object of the form:

    { "session": <session-id> }

and returns

    { "success": true,
      "code": <finish-code> }

Where <finish-code> is a unique code that the user can provide to Mechanical
Turk to prove that they finished a session as requested.

## License

Copyright © 2015 SRI International

Distributed under the Eclipse Public License either version 1.0 or (at your option) any later version.
