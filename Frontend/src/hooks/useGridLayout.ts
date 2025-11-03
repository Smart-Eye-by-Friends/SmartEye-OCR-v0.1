// src/hooks/useGridLayout.ts
import { useState, useCallback } from 'react'

interface UseGridLayoutReturn {
  isSliderCollapsed: boolean
  closeSlider: () => void
  openSlider: () => void
  toggleSlider: () => void
}

export const useGridLayout = (): UseGridLayoutReturn => {
  const [isSliderCollapsed, setIsSliderCollapsed] = useState(false)

  const closeSlider = useCallback(() => {
    setIsSliderCollapsed(true)
  }, [])

  const openSlider = useCallback(() => {
    setIsSliderCollapsed(false)
  }, [])

  const toggleSlider = useCallback(() => {
    setIsSliderCollapsed((prev) => !prev)
  }, [])

  return {
    isSliderCollapsed,
    closeSlider,
    openSlider,
    toggleSlider,
  }
}
