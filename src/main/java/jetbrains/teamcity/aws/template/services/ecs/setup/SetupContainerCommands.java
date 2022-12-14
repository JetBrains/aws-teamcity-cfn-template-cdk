package jetbrains.teamcity.aws.template.services.ecs.setup;

public class SetupContainerCommands {
    public final static String createDatabaseSh =
            "# Define DATADIR env variable in ECS task\n" +
            "CONFDIR=${DATADIR}/config\n" +
            "\n" +
            "if [ ! -f \"${CONFDIR}/database.properties\" ]; then\n" +
            "  echo \"Trying to drop database '${DB}' because database.properties file does not exist.\"\n" +
            "  dropdb --if-exists \"${DB}\"\n" +
            "fi\n" +
            "\n" +
            "if createdb \"${DB}\"; then\n" +
            "  echo \"Database ${DB} created successfully\"\n" +
            "  createuser \"${DB}\"\n" +
            "  # TODO write password to SSM parameter\n" +
            "  PASSWORD=$(tr -dc '[:alpha:]' < /dev/urandom | fold -w \"${1:-20}\" | head -n 1)\n" +
            "  echo \"GRANT ALL PRIVILEGES ON DATABASE \\\"${DB}\\\" TO \\\"${DB}\\\"\" | psql postgres\n" +
            "  echo \"ALTER USER \\\"${DB}\\\" WITH ENCRYPTED PASSWORD '${PASSWORD}'\" | psql postgres\n" +
            "\n" +
            "  mkdir -p \"${CONFDIR}\"\n" +
            "  {\n" +
            "    printf \"connectionUrl=jdbc:postgresql://%s:%s/%s\\n\" \"${PGHOST}\" \"${PGPORT}\" \"${DB}\"\n" +
            "    printf \"connectionProperties.user=%s\\n\" \"${DB}\"\n" +
            "    printf \"connectionProperties.password=%s\\n\" \"${PASSWORD}\"\n" +
            "  } >> \"${CONFDIR}/database.properties\"\n" +
            "else\n" +
            "  echo \"Database ${DB} exists already.\"\n" +
            "fi\n" +
            "\n" +
            "echo \"Copying jdbc postgresql-42.3.4.jar to ${DATADIR}/lib/jdbc\"\n" +
            "mkdir -p \"${DATADIR}/lib/jdbc\"\n" +
            "cp /usr/local/lib/jdbc/postgresql-42.3.4.jar \"${DATADIR}/lib/jdbc\"\n" +
            "\n" +
            "chown 1000:1000 -R \"$DATADIR\" \"$LOGSDIR\"\n" +
            "\n" +
            "echo \"/////////////Database Setup COMPLETED\"";
}
