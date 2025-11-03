// src/hooks/useGridLayout.ts
import { useState, useCallback } from 'react'

export const useGridLayout = () => {
  const [isSliderCollapsed, setIsSliderCollapsed] = useState(false)

  const toggleSlider = useCallback(() => {
    setIsSliderCollapsed(prev => !prev)
  }, [])

  const openSlider = useCallback(() => {
    setIsSliderCollapsed(false)
  }, [])

  const closeSlider = useCallback(() => {
    setIsSliderCollapsed(true)
  }, [])

  return {
    isSliderCollapsed,
    toggleSlider,
    openSlider,
    closeSlider
  }
}
