# Cloud Operations Utils. - JDBC SQLCommenter #

## Usage ##

This uses the `java.sql.Driver` Service Loader mechanism so to use simply insert `:commenter`. Here are a few examples:

```
jdbc:postgresql://host/database
jdbc:h2:mem:test
```

...this would become:
```
jdbc:commenter:postgresql://host/database
jdbc:commenter:h2:mem:test
```
