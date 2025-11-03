import { useState, useEffect } from "react";

export const useImageOptimization = (imageUrl: string) => {
  const [optimizedUrl, setOptimizedUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!imageUrl) {
      setOptimizedUrl(null);
      setIsLoading(false);
      return;
    }

    const img = new Image();
    img.onload = () => {
      setOptimizedUrl(imageUrl);
      setIsLoading(false);
    };
    img.onerror = () => {
      console.error("Image load error:", imageUrl);
      setOptimizedUrl(null);
      setIsLoading(false);
    };
    img.src = imageUrl;

    return () => {
      img.onload = null;
      img.onerror = null;
    };
  }, [imageUrl]);

  return {
    optimizedUrl,
    isLoading,
  };
};
