<?php

namespace App\Http\Controllers;

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Http;
use Illuminate\Support\Facades\Log;

/**
 * Tiny Vault - Laravel Example Controller
 * 
 * This controller demonstrates how to upload a file to the Storage API 
 * from a Laravel backend using Laravel's Http facade.
 */
class StorageController extends Controller
{
    public function uploadToVault(Request $request)
    {
        // 1. Validate the incoming file
        $request->validate([
            'document' => 'required|file|max:50000', // 50MB max from your users
        ]);

        $file = $request->file('document');

        // Load credentials from your .env file
        $storageUrl = env('STORAGE_API_URL', 'https://storage.devyoussef.com/api/v1');
        $apiKey = env('STORAGE_API_KEY');
        $apiSecret = env('STORAGE_API_SECRET');

        try {
            // 2. Upload the file to Tiny Vault
            // Attach ?public=true if you want to store it publicly and get an embeddable URL
            $response = Http::withHeaders([
                'X-API-Key' => $apiKey,
                'X-API-Secret' => $apiSecret,
            ])
            ->attach(
                'file', // The name of the field the API expects
                file_get_contents($file->getRealPath()), 
                $file->getClientOriginalName()
            )
            ->post("{$storageUrl}/upload", [
                'public' => 'true' // Query parameters can be passed here or in the URL directly
            ]);

            // 3. Handle the response
            if ($response->successful()) {
                $data = $response->json();
                
                // Example: save the publicUrl to your Laravel Database
                // auth()->user()->update(['profile_picture_url' => $data['publicUrl']]);

                return response()->json([
                    'message' => 'Successfully uploaded to Tiny Vault!',
                    'vault_url' => $data['publicUrl'],
                    'file_metadata' => $data
                ]);
            }

            // 4. Handle API Errors
            Log::error('Tiny Vault upload failed', [
                'status' => $response->status(),
                'body' => $response->body()
            ]);

            return response()->json([
                'error' => 'Failed to store the file securely.'
            ], 500);

        } catch (\Exception $e) {
            Log::error('Tiny Vault connection error: ' . $e->getMessage());
            return response()->json(['error' => 'Storage service is unreachable.'], 500);
        }
    }
}
