// src/hooks/useResponsive.ts
import { useState, useEffect } from 'react'

export type Breakpoint = 'xs' | 'sm' | 'md' | 'lg' | 'xl'

const getBreakpoint = (width: number): Breakpoint => {
  if (width < 1366) return 'xs'        // 1280px 이하
  if (width < 1600) return 'sm'        // 1366px ~ 1599px
  if (width < 1920) return 'md'        // 1600px ~ 1919px
  if (width < 2560) return 'lg'        // 1920px ~ 2559px
  return 'xl'                          // 2560px 이상
}

export const useResponsive = () => {
  const [screenWidth, setScreenWidth] = useState(window.innerWidth)
  const [screenHeight, setScreenHeight] = useState(window.innerHeight)
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(getBreakpoint(window.innerWidth))

  useEffect(() => {
    const handleResize = () => {
      const width = window.innerWidth
      setScreenWidth(width)
      setScreenHeight(window.innerHeight)
      setBreakpoint(getBreakpoint(width))
    }

    window.addEventListener('resize', handleResize)
    return () => window.removeEventListener('resize', handleResize)
  }, [])

  return {
    screenWidth,
    screenHeight,
    breakpoint
  }
}
