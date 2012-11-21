# jenkins-clojure-injector

jenkins-clojure-injector is a lein2 plugin for generating a jenkins
plugin that injects arbitrary clojure code in to the jenkins jvm.

It generates a jenkins plugin class that calls your clojure code on load.

## Usage

Put `[jenkins-clojure-injector "0.1.0-SNAPSHOT"]` into the `:plugins`
vector of your project.clj.

add the name of your main function to your project.clj under the
:jenkins-inject key

    :jenkins-inject foo/bar

and

    $ lein jpi

## License

Copyright © 2012 Kevin Downey

Distributed under the Eclipse Public License, the same as Clojure.
