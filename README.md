Create a Cassandra container:

```shell
docker run --name cassandra -p 9042:9042 -d cassandra:latest
```

Log into Cassandra to create table:

```shell
docker exec -it cassandra cqlsh
```

Create a keyspace:

```cql
create keyspace my_keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};
```

Use the keyspace:

```cql
use my_keyspace;
```

Create the `people` table:

```cql
CREATE TABLE IF NOT EXISTS people(
    id UUID,
    first_name TEXT,
    last_name TEXT,
    age INT,
    job TEXT,
    PRIMARY KEY((id))
);
```