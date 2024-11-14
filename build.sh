TAG="$1"
BIN_VER="null"
VERSION_PATTERN="v([0-9.]){5}[+](release[.][0-9]|debug)"

# $1 Key | $2 Value
function set_output() {
    echo "$1=$2" >> $GITHUB_OUTPUT
}

function update_binary_version() {
    NEW_VERSION=$1
    sed -i'' -E "s#$VERSION_PATTERN#$NEW_VERSION#g" gradle.properties
}

function get_bin_ver() {
    # PROP=$(cat gradle.properties)
    BIN_VER=$(grep -Eo "$VERSION_PATTERN" gradle.properties)
    echo "Got version: $BIN_VER"
}

echo "Updating binary version"
if [[ "$TAG" != refs/tags/v* ]]; then
    echo "Seems like build is debug"
    get_bin_ver

    REAL=$(echo $BIN_VER | sed -E "s#(release[.][0-9]|debug)#debug#g")
    echo "Gonna set BIN_VER to this $REAL"

    update_binary_version $REAL
    get_bin_ver
else
    TAG="${TAG#refs/tags/}"
    echo "Build is release, using $TAG as the version"

    # set_binary_build_type "release"
    update_binary_version $TAG
    get_bin_ver

    echo "Setting build output: binver=$BIN_VER"
    set_output "binver" $BIN_VER
fi
echo "New version: $BIN_VER"

echo "Building with GradleW"
./gradlew build --no-daemon --configure-on-demand "-Dorg.gradle.parralel=true"