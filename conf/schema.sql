-- note: 'user' is a keyword in posgres, so we need those quotes
CREATE TABLE "user" (
  username TEXT NOT NULL PRIMARY KEY,
  email TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  member_since TIMESTAMP WITH TIME ZONE NOT NULL,
  real_name TEXT,
  bio TEXT,
  website TEXT,
  quota_mb INT NOT NULL DEFAULT 200,
  active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE user_role (
  id SERIAL PRIMARY KEY,
  username TEXT NOT NULL REFERENCES "user"(username),
  has_role TEXT NOT NULL
);

-- a default admin user with username=password=recogito
INSERT INTO "user" (username, email, password_hash, salt, member_since) VALUES (
  'recogito',
  'recogito@example.com',
  'b875551ec64627a66f707ffc9f22a5e62ebc43617fe6b55bca19436ddf4d8ae2',
  'gazC7r/l6ztdpG39+4BWhv46YizBY32EC40V2cojhAY=',
  '2016-06-28 16:40:50.311+02'
);

INSERT INTO user_role (username, has_role) VALUES ('recogito', 'ADMIN');

-- staging area for documents during upload workflow
CREATE TABLE upload (
  id SERIAL PRIMARY KEY,
  owner TEXT NOT NULL UNIQUE REFERENCES "user"(username),
  created_at TIMESTAMP WITH TIME ZONE NOT NULL,
  title TEXT NOT NULL,
  author TEXT,
  date_freeform TEXT,
  description TEXT,
  language TEXT,
  source TEXT,
  edition TEXT,
  license TEXT
);

CREATE TABLE upload_filepart (
  id UUID PRIMARY KEY,
  upload_id INTEGER NOT NULL REFERENCES upload(id) ON DELETE CASCADE,
  owner TEXT NOT NULL REFERENCES "user"(username),
  title TEXT NOT NULL,
  content_type TEXT NOT NULL,
  file TEXT NOT NULL,
  filesize_kb DOUBLE PRECISION,
  -- TODO filepart metadata (source, identifier,... ?)
  UNIQUE (owner, title)
);

-- users own (and can share) documents
CREATE TABLE document (
  id TEXT NOT NULL PRIMARY KEY,
  owner TEXT NOT NULL REFERENCES "user"(username),
  uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL,
  title TEXT NOT NULL,
  author TEXT,
  date_numeric TIMESTAMP,
  date_freeform TEXT,
  description TEXT,
  language TEXT,
  source TEXT,
  edition TEXT,
  license TEXT,
  is_public BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE document_filepart (
  id UUID PRIMARY KEY,
  document_id TEXT NOT NULL REFERENCES document(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  content_type TEXT NOT NULL,
  file TEXT NOT NULL,
  sequence_no INTEGER NOT NULL
  -- TODO filepart metadata (source, identifier,... ?)
);

-- users can organize documents into folders
CREATE TABLE folder (
  id SERIAL PRIMARY KEY,
  owner TEXT NOT NULL REFERENCES "user"(username),
  title TEXT NOT NULL,
  -- if parent is empty then it's a root folder
  parent INTEGER REFERENCES folder(id)
);

CREATE TABLE folder_association (
  folder_id INTEGER NOT NULL REFERENCES folder(id),
  document_id TEXT NOT NULL REFERENCES document(id)
);

-- teams are a first level entities similar to user
-- CREATE TABLE team (
--   title TEXT NOT NULL PRIMARY KEY,
--   created_by TEXT NOT NULL REFERENCES "user"(username),
--   created_at TIMESTAMP WITH TIME ZONE NOT NULL
-- );

-- CREATE TABLE team_membership (
--   username TEXT NOT NULL REFERENCES "user"(username),
--   team TEXT NOT NULL REFERENCES team(title),
--   member_since TIMESTAMP WITH TIME ZONE NOT NULL
-- );

-- ledger of shared documents and folders
CREATE TABLE sharing_policy (
  id SERIAL PRIMARY KEY,
  -- one of the following two needs to be defined
  folder_id INTEGER REFERENCES folder(id),
  document_id TEXT REFERENCES document(id),
  shared_by TEXT NOT NULL REFERENCES "user"(username),
  shared_with TEXT NOT NULL REFERENCES "user"(username),
  shared_at TIMESTAMP WITH TIME ZONE NOT NULL,
  access_level TEXT NOT NULL,
  UNIQUE (document_id, shared_with)
);

-- keep a log of what happened for shared elements
-- e.g. to inform users about what happened
-- CREATE TABLE sharing_event_log (
--   id SERIAL PRIMARY KEY,
--   type_of_action TEXT,
--   action_by TEXT NOT NULL REFERENCES "user"(username),
--   action_at TIMESTAMP WITH TIME ZONE NOT NULL,
--   policy_id INTEGER NOT NULL REFERENCES sharing_policy(id)
-- );
