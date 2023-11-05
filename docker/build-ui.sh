# clone and build conductor UI
mkdir -p tmp/ui
cd tmp/ui
pwd
git clone https://github.com/Netflix/conductor
cd conductor/ui
yarn config set network-timeout 600000 -g
yarn install
yarn build