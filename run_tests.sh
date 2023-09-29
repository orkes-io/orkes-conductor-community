./gradlew clean build
# Create an empty ui build directory... we don't need UI for this build
mkdir -p docker/tmp/ui/conductor/ui/build
docker rm -f  conductor_test_container
docker build -f docker/DockerfileStandalone . -t conductor_test_container
docker run -dit --name conductor_test_container -p 8899:8080 -p 4535:5000 -t conductor_test_container
while ! curl -s http://localhost:8899/api/metadata/workflow -o /dev/null
do
  echo "$(date) - still trying"
  sleep 1
done
sleep 5
echo "All set - starting tests now"
./gradlew clean
./gradlew -PIntegrationTests orkes-conductor-test-harness:test
docker rm -f  conductor_test_container

