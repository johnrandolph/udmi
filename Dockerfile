FROM ubuntu:latest AS base
WORKDIR /udmi
COPY bin bin
COPY etc etc
COPY venv venv
RUN apt-get update && apt-get -y install sudo

FROM ubuntu:latest AS base_pkgs
WORKDIR /udmi
COPY --from=base / /
RUN apt-get install -y libexpat1-dev
RUN apt-get install -y jq

FROM python:3 AS python_deps
WORKDIR /udmi
COPY --from=base_pkgs / /
RUN bin/run_tests install_dependencies

FROM base AS udmi_site_model
ARG udmi_site_model
WORKDIR /udmi
#RUN mkdir -p foox
#RUN mkdir -p sites/udmi_site_model
#COPY $UDMI_SITE_MODEL sites/udmi_site_model
#COPY $UDMI_SITE_MODEL foox

FROM eclipse-temurin:latest AS src
WORKDIR /udmi
COPY --from=udmi_site_model / /
COPY --from=python_deps / /
COPY docs docs
COPY gencode gencode
COPY misc misc
COPY pubber pubber
COPY schema schema
COPY tests tests
COPY udmis udmis
COPY validator validator
