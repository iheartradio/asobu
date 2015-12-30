## Goal

Building a play frontend for an akka cluster. The frontend provides

* A job-execute frontend to calculate the factorial

## Getting Started

Run each line in a new terminal.

```
sbt "backend/run 2551"
sbt "project frontend" run
```

goto

http://localhost:9000/api/normal/factorialOf/5

http://localhost:9000/api/dsl/factorialOf/5
