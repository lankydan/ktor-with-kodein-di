create keyspace my_keyspace WITH replication = {'class': 'SimpleStrategy', 'replication_factor' : 1};
  
CREATE TABLE IF NOT EXISTS people(
    id UUID,
    first_name TEXT,
    last_name TEXT,
    age INT,
    job TEXT,
    PRIMARY KEY((id))
);