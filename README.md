# Akka Dining Hell

Example projects of using Akka typed and untyped actors.

* `dining` package contains typed and untyped implementations of the Dining Philosophers Problem. 
  * The description of the problem can be found on [Wikipedia](https://en.wikipedia.org/wiki/Dining_philosophers_problem), and the solution is inspired by the [DiningHakkersOnBecome](https://en.wikipedia.org/wiki/Dining_philosophers_problem) official Akka example, which is an adaptation of [this solution](http://www.dalnefre.com/wp/2010/08/dining-philosophers-in-humus/).
  * While in the original formulation the philosophers were using forks, we'll use chopsticks to make it evident that they need two of them in order to eat.  
* `dininghell` extends the Dining Philosophers Problem to illustrate actor supervision and monitoring.
  * Here we introduce a `God` that created and supervises the other actors and a `Devil` that wants to do evil.
  * The `Devil` creates a bomb and sends it to the God and the philosophers, and the bomb is passed between the God and a random philosopher until it explodes. When it explodes, the philosopher actor is stoped and the supervisor restarts it. 
  * The `Devil` tries his best to make sure always a bomb is circulating between philosophers, but not more than one. In order to do that, he monitors the philosophers for termination and assumes that his bomb exploded.

### How to run

```
$ sbt run
```
### To do

* fix monitoring
* add tests
* add java implementations

