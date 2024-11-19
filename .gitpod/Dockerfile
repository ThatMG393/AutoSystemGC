FROM gitpod/workspace-full-vnc

SHELL ["/bin/bash", "-c"]

RUN sudo apt-get update \
    && sudo apt-get install -y libx11-dev libxkbfile-dev libsecret-1-dev libgconf2-dev libnss3 libgtk-3-dev libasound2-dev twm \
    && sudo apt-get clean
    
RUN sudo rm -rf /var/cache/apt/* \
    && sudo rm -rf /var/lib/apt/lists/* \
    && sudo rm -rf /tmp/*

ENV HOME_PATH="/home/gitpod"
ENV SDKMAN_PATH="${HOME_PATH}/.sdkman"

# java
RUN rm -rf ${SDKMAN_PATH} && curl -s 'https://get.sdkman.io' | bash
RUN source "${SDKMAN_PATH}/bin/sdkman-init.sh" && sdk install java 21.0.5-graal
