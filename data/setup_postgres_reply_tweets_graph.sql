#SELECT pg_terminate_backend(pg_stat_activity.pid)
#FROM pg_stat_activity WHERE pg_stat_activity.datname =
#'graphtweet' AND pid <> pg_backend_pid();
#DROP DATABASE IF EXISTS graphtweet;

create user graphuser with password 'graphuser' createdb;


CREATE DATABASE graphtweet;

\c graphtweet



DROP TABLE IF EXISTS replytweets;

create table replytweets(
tweet_from bigint not null,
from_create_at timestamp not null,
from_text text not null,
from_longitude double precision,
from_latitude double precision,
tweet_to bigint not null,
to_create_at timestamp not null,
to_text text not null,
to_longitude double precision,
to_latitude double precision,
PRIMARY KEY (tweet_from));

#the below steps will take a long time considering
#the large amount of data
\copy replytweets from 'graph1.csv' (FORMAT CSV);

create index inverted_index_to_text on replytweets using gin(to_tsvector('english', to_text));

create index inverted_index_from_text on replytweets using gin(to_tsvector('english', from_text));

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO graphuser;
