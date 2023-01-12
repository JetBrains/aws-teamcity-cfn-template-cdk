/*
 * Copyright 2000-2023 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.teamcity.aws.template.services.ecs.setup;

import jetbrains.teamcity.aws.template.services.rds.AwsTeamCityTemplateRds;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class SetupContainerParameters {
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

    public static Map<String, String> getEnvVarParameters(@NotNull final AwsTeamCityTemplateRds teamcityTemplateRds) {
        Map<String, String> envVarsMap = new HashMap<>();
        envVarsMap.put("PGHOST", teamcityTemplateRds.getDatabaseInstance().getInstanceEndpoint().getHostname());
        envVarsMap.put("PGPORT", "5432");
        envVarsMap.put("PGUSER", AwsTeamCityTemplateRds.DATABASE_USERNAME);
        envVarsMap.put("PGPASSWORD", AwsTeamCityTemplateRds.DATABASE_MASTER_PASSWORD);

        envVarsMap.put("DB", AwsTeamCityTemplateRds.DATABASE_NAME);
        envVarsMap.put("DATADIR", "/data/teamcity_server/datadir");
        envVarsMap.put("LOGSDIR", "/opt/teamcity/logs");
        envVarsMap.put("TEAMCITY_SERVER_OPTS", "-Dteamcity.startup.maintenance=false");
        return envVarsMap;
    }
}
