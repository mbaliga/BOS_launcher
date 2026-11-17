cmake_minimum_required(VERSION 3.16)
project(liftoff NONE)

# Pure-QML Click (Clickable builder: pure-qml-cmake). Architecture-independent payload; the
# manifest architecture field is filled in from CLICK_ARCH by Clickable.
set(APP_NAME liftoff)


# Clickable fills @CLICK_ARCH@ / @CLICK_FRAMEWORK@ in manifest.json itself.
install(FILES manifest.json.in RENAME manifest.json DESTINATION ${CMAKE_INSTALL_PREFIX})
install(FILES liftoff.apparmor liftoff.desktop DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY qml DESTINATION ${CMAKE_INSTALL_PREFIX})
install(DIRECTORY assets DESTINATION ${CMAKE_INSTALL_PREFIX})
