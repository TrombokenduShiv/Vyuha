import './style.css'
import * as THREE from 'three'
import gsap from 'gsap'

// --- 1. Scene Setup ---
const canvas = document.querySelector('#webgl-canvas')
const scene = new THREE.Scene()

const sizes = {
  width: window.innerWidth,
  height: window.innerHeight
}

const camera = new THREE.PerspectiveCamera(75, sizes.width / sizes.height, 0.1, 1000)
camera.position.z = 5
scene.add(camera)

const renderer = new THREE.WebGLRenderer({
  canvas: canvas,
  antialias: true,
  alpha: true
})
renderer.setSize(sizes.width, sizes.height)
renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
renderer.shadowMap.enabled = true
renderer.shadowMap.type = THREE.PCFSoftShadowMap

// --- 2. Lighting ---
const ambientLight = new THREE.AmbientLight(0xffffff, 0.5)
scene.add(ambientLight)

const directionalLight = new THREE.DirectionalLight(0xffffff, 1)
directionalLight.position.set(5, 5, 5)
directionalLight.castShadow = true
scene.add(directionalLight)

// --- 3. Resize Handling ---
window.addEventListener('resize', () => {
  sizes.width = window.innerWidth
  sizes.height = window.innerHeight

  camera.aspect = sizes.width / sizes.height
  camera.updateProjectionMatrix()

  renderer.setSize(sizes.width, sizes.height)
  renderer.setPixelRatio(Math.min(window.devicePixelRatio, 2))
})

// --- 4. Injection Points ---
function buildTruck() {
  /* ASTRA INJECTION POINT: 3D Geometry and GSAP Logic goes here */
}

function buildLetters() {
  /* ASTRA INJECTION POINT: 3D Geometry and GSAP Logic goes here */
}

function initAnimationTimeline() {
  /* ASTRA INJECTION POINT: 3D Geometry and GSAP Logic goes here */
}

// Call injection points
buildTruck()
buildLetters()
initAnimationTimeline()

// --- 5. Render Loop ---
const clock = new THREE.Clock()

const tick = () => {
  const elapsedTime = clock.getElapsedTime()

  // Render
  renderer.render(scene, camera)

  // Call tick again on the next frame
  window.requestAnimationFrame(tick)
}

tick()
