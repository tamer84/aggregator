set -e

echo "===================> Initiating $ENVIRONMENT workspace"
terraform init -no-color
terraform workspace select "$ENVIRONMENT" -no-color
terraform validate -no-color
terraform init -no-color

echo "===========> Generating plan"
TF_VAR_application_version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout) terraform plan -no-color -out=plan.bin

if [ -f plan.bin ]; then
    echo "===========> Applying changes on the plan"
    terraform apply -auto-approve -no-color -input=false plan.bin
else
    echo "!!!!!!!!!!!!! Plan not found!"
fi
