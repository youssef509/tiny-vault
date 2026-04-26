/**
 * Tiny Vault - Next.js / TypeScript Example
 * 
 * This example shows how to upload a file to your Storage API from a Next.js 
 * App Router API Route (e.g., `app/api/upload/route.ts`).
 */

import { NextResponse } from 'next/server';

// Load your credentials from environment variables (.env.local)
const STORAGE_URL = process.env.STORAGE_API_URL || 'https://storage.devyoussef.com/api/v1';
const API_KEY = process.env.STORAGE_API_KEY || '';
const API_SECRET = process.env.STORAGE_API_SECRET || '';

export async function POST(request: Request) {
  try {
    const formData = await request.formData();
    const file = formData.get('file') as File;

    if (!file) {
      return NextResponse.json({ error: "No file provided" }, { status: 400 });
    }

    // 1. Prepare the Form Data for the Storage API
    const apiFormData = new FormData();
    apiFormData.append('file', file);

    // 2. Upload the file to Tiny Vault
    // Note: appending '?public=true' tells the API to make this file accessible 
    // to everyone so you can embed it in an <img> tag on your frontend.
    const response = await fetch(`${STORAGE_URL}/upload?public=true`, {
      method: 'POST',
      headers: {
        // Authenticate with your secure keys
        'X-API-Key': API_KEY,
        'X-API-Secret': API_SECRET,
      },
      body: apiFormData,
    });

    if (!response.ok) {
      const errorText = await response.text();
      console.error("Storage API Error:", errorText);
      return NextResponse.json({ error: "Failed to upload to storage" }, { status: 500 });
    }

    const data = await response.json();

    // 3. Save the returned `publicUrl` to your Next.js Database
    // await prisma.userProfile.update({ data: { avatarUrl: data.publicUrl } })

    // 4. Return success to your frontend
    return NextResponse.json({
      success: true,
      message: "File stored securely!",
      fileData: data, 
      // Example of 'data' payload:
      // {
      //   "filename": "uuid.jpg",
      //   "originalFilename": "my-avatar.jpg",
      //   "publicUrl": "https://storage.devyoussef.com/api/v1/public/uuid.jpg",
      //   ...
      // }
    });

  } catch (error) {
    console.error(error);
    return NextResponse.json({ error: "Internal Server Error" }, { status: 500 });
  }
}
