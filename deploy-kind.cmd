@echo off

echo ===============================
echo Building Docker Images
echo ===============================

docker build -t product-service:1.0 ./services/product-service
docker build -t inventory-service:1.0 ./services/inventory-service
docker build -t order-service:1.0 ./services/order-service

echo ===============================
echo Loading Images Into Kind
echo ===============================

set PATH=%PATH%;C:\labs\kind.exe

cd ../
kind load docker-image product-service:1.0 --name microservices-lab
kind load docker-image inventory-service:1.0 --name microservices-lab
kind load docker-image order-service:1.0 --name microservices-lab

cd kong-microservices-ref-app

echo ===============================
echo Applying Kubernetes YAMLs
echo ===============================


kubectl apply -f services/product-service/product-service.yaml
kubectl apply -f services/inventory-service/inventory-service.yaml
kubectl apply -f services/order-service/order-service.yaml

echo ===============================
echo Deployment Complete
echo ===============================

kubectl get pods
kubectl get svc

pause
