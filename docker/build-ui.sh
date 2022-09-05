# clone and build conductor UI
mkdir -p tmp/ui
cd tmp/ui
pwd
git clone https://github.com/Netflix/conductor
cd conductor/ui
yarn install
yarn build