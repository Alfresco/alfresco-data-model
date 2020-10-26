## This project has now been Archived

The alfresco-core, alfresco-data-model, alfresco-repository and alfresco-remote-api projects have been archived with their code incorporated into [alfresco-community-repo]( https://github.com/Alfresco/alfresco-community-repo) to simply ongoing development. The same artifacts are still produced by the new project. It also has a branch used as the basis of each of ACS 6 Enterprise release. For more information, set the new project’s README.md file.

### Alfresco Data Model
[![Build Status](https://travis-ci.com/Alfresco/alfresco-data-model.svg?branch=master)](https://travis-ci.com/Alfresco/alfresco-data-model)

Data model is a library packaged as a jar file which is part of [Alfresco Content Services Repository](https://community.alfresco.com/docs/DOC-6385-project-overview-repository).
The library contains the following:
* Dictionary, Repository and Search Services interfaces
* Models for data types and Dictionary implementation
* Parsers

Please note that the data model uses version 2 of the Jackson libraries. 
The upgrade from version 1 was not backward compatible, any projects
that are dependent on data model using Jackson 1.x should use the data-model 6.N branch. 

Version 8.0 of data-model depends on alfresco-core 7.0 which is based on Spring 5.
 

### Building and testing
The project can be built and tested by running Maven command:
~~~
mvn clean install
~~~

### Artifacts
The artifacts can be obtained by:
* downloading from [Alfresco repository](https://artifacts.alfresco.com/nexus/content/groups/public)
* getting as Maven dependency by adding the dependency to your pom file:
~~~
<dependency>
  <groupId>org.alfresco</groupId>
  <artifactId>alfresco-data-model</artifactId>
  <version>version</version>
</dependency>
~~~
and Alfresco repository:
~~~
<repository>
  <id>alfresco-maven-repo</id>
  <url>https://artifacts.alfresco.com/nexus/content/groups/public</url>
</repository>
~~~
The SNAPSHOT version of the artifact is **never** published.

### Old version history
The history for older versions can be found in [Alfresco SVN](https://svn.alfresco.com/repos/alfresco-open-mirror/alfresco/HEAD/root/projects/data-model)

### Contributing guide
Please use [this guide](CONTRIBUTING.md) to make a contribution to the project.
