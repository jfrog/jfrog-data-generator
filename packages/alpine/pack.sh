#!/usr/bin/env bash

echo "Going to pack generated files..."
artifacts=($(ls artifacts))

for artifact in "${artifacts[@]}"; do
  echo "Packing $artifact..."
  cd artifacts/"$artifact" && \
  tar -cv .PKGINFO | abuild-tar --cut | gzip -9 > control.tar.gz && \
  tar -cv usr/ | abuild-tar --hash | gzip -9 > data.tar.gz && \
  cat control.tar.gz data.tar.gz > ../../generated/"$artifact".apk && cd ../../;
  echo "Artifact $artifact has been created!"
done

find generated -name "*.apk" -exec basename {} \; > generated/10000alp.csv

echo "=================================="
echo "All artifacts has been generated!"
echo "=================================="
echo "Going to create zip archive with generated artifacts"
zip -s 500m -j generated/10000alpine.zip generated/*.apk && echo "ZIP has been created" || echo "Something went wrong (:"

# to unzip it
# zip -FF generated/10000alpine.zip --out10000alpine.zip full && unzip out10000alpine.zip

exit 0;