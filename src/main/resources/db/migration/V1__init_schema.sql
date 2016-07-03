CREATE TABLE SOURCES (
  ID BIGSERIAL PRIMARY KEY,
  TELEGRAM_CHAT_ID BIGINT NOT NULL UNIQUE
);

CREATE TABLE TAGS (
  ID BIGSERIAL PRIMARY KEY,
  NAME VARCHAR(200) NOT NULL
);

CREATE TABLE MESSAGES (
  ID BIGSERIAL PRIMARY KEY,
  TELEGRAM_MESSAGE_ID BIGINT NOT NULL UNIQUE,
  MESSAGE TEXT NOT NULL
);

CREATE TABLE MESSAGES_SOURCES (
  MESSAGE_ID BIGINT NOT NULL REFERENCES MESSAGES(ID) ON DELETE CASCADE,
  SOURCE_ID BIGINT NOT NULL REFERENCES SOURCES(ID) ON DELETE CASCADE,

  PRIMARY KEY (MESSAGE_ID, SOURCE_ID)
);

CREATE TABLE MESSAGES_TAGS (
  MESSAGE_ID BIGINT NOT NULL REFERENCES MESSAGES(ID) ON DELETE CASCADE,
  TAG_ID BIGINT NOT NULL REFERENCES TAGS(ID) ON DELETE CASCADE,

  PRIMARY KEY (MESSAGE_ID, TAG_ID)
);